package org.joinmastodon.android.ui.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Xml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

// MAMMUT: The Caption Track (docs/CAPTION-TRACK-SPEC.md §2). Reads an image's own
// metadata to prefill the composer description field — a draft the author can edit,
// never anything invented. Sources, first non-empty wins:
//  1. EXIF ImageDescription (tag 270)
//  2. XMP dc:description (rdf:Alt items, preferring x-default, then en)
//  3. IPTC caption as exposed in XMP: photoshop:Headline, then Iptc4xmpCore:Caption (rdf:Alt)
//  4. Windows XPTitle, then XPSubject (EXIF IFD0 tags 0x9C9B / 0x9C9E, UTF-16LE — the
//     androidx library does not register these, so IFD0 is walked directly)
//
// Local content URIs only, images only, and the caller must run the read off the main
// thread. No network, no AI text: only what the file says.

public class ImageDescriptionMetadata{
	public static final int MAX_DESCRIPTION_LENGTH=1500;

	// EXIF IFD0 tags for the Windows XP* UTF-16LE strings
	private static final int TAG_XP_TITLE=0x9C9B;
	private static final int TAG_XP_SUBJECT=0x9C9E;

	private ImageDescriptionMetadata(){}

	/**
	 * Reads the first available description from the image at the given local content Uri.
	 * Must not be called on the main thread. Returns null when nothing usable is found.
	 */
	@Nullable
	public static String read(Context context, Uri uri) throws IOException{
		if(!"content".equalsIgnoreCase(uri.getScheme()))
			return null;
		String type=context.getContentResolver().getType(uri);
		if(type==null || !type.startsWith("image/"))
			return null;
		try(InputStream in=context.getContentResolver().openInputStream(uri)){
			if(in==null)
				return null;
			return read(in);
		}
	}

	/**
	 * Reads the first available description from an image stream (JPEG/PNG/WEBP).
	 * Pure plumbing over the pure helpers below; kept separate so those stay testable.
	 */
	@Nullable
	public static String read(InputStream in) throws IOException{
		byte[] data=readAllBytes(in);
		ExifInterface exif=new ExifInterface(new ByteArrayInputStream(data));

		// 1. EXIF ImageDescription
		String desc=take(exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION));
		if(desc!=null)
			return desc;

		// 2/3. XMP: dc:description, then IPTC caption fields
		byte[] xmp=exif.getAttributeBytes(ExifInterface.TAG_XMP);
		if(xmp!=null && xmp.length>0){
			desc=take(extractXmpDescription(new String(xmp, StandardCharsets.UTF_8)));
			if(desc!=null)
				return desc;
		}

		// 4. Windows XP tags
		return take(readXpTitleOrSubject(data));
	}

	/**
	 * Extracts a description from an XMP packet, in source order:
	 * dc:description (rdf:Alt, x-default then en), photoshop:Headline,
	 * Iptc4xmpCore:Caption (rdf:Alt, x-default then en).
	 */
	@Nullable
	public static String extractXmpDescription(@Nullable String xmp){
		if(xmp==null || xmp.isEmpty())
			return null;
		try{
			org.xmlpull.v1.XmlPullParser parser=Xml.newPullParser();
			parser.setInput(new StringReader(xmp));
			return extractXmpDescription(parser);
		}catch(Exception x){
			return null;
		}
	}

	/**
	 * XMP parsing over an injected XmlPullParser so the rule can run on the JVM in a
	 * scratch harness. Element local names are matched (dc:description, photoshop:Headline,
	 * Iptc4xmpCore:Caption) — those names are unambiguous within XMP rdf:RDF packets.
	 */
	@Nullable
	public static String extractXmpDescription(org.xmlpull.v1.XmlPullParser parser) throws org.xmlpull.v1.XmlPullParserException, IOException{
		String dcDescription=null, caption=null;
		String altOwner=null; // local name of the LangAlt property being read ("description" or "Caption")
		boolean inAlt=false, inHeadline=false;
		StringBuilder headline=new StringBuilder();
		LangAlt alt=null;
		String liLang=null, liText=null;

		int event;
		while((event=parser.next())!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT){
			switch(event){
				case org.xmlpull.v1.XmlPullParser.START_TAG -> {
					String name=localName(parser.getName());
					if("description".equals(name) || "Caption".equals(name)){
						altOwner=name;
					}else if("Alt".equals(name) && altOwner!=null){
						inAlt=true;
						alt=new LangAlt();
					}else if("li".equals(name) && inAlt){
						liLang=parser.getAttributeValue("http://www.w3.org/XML/1998/namespace", "lang");
						liText="";
					}else if("Headline".equals(name)){
						inHeadline=true;
					}
				}
				case org.xmlpull.v1.XmlPullParser.TEXT, org.xmlpull.v1.XmlPullParser.CDSECT -> {
					if(liText!=null)
						liText+=parser.getText();
					if(inHeadline && parser.getText()!=null)
						headline.append(parser.getText());
				}
				case org.xmlpull.v1.XmlPullParser.END_TAG -> {
					String name=localName(parser.getName());
					if("li".equals(name) && inAlt && liText!=null){
						String t=liText.trim();
						liText=null;
						if(!t.isEmpty()){
							if(alt.first==null)
								alt.first=t;
							if("x-default".equals(liLang))
								alt.xDefault=t;
							else if("en".equals(liLang))
								alt.en=t;
						}
					}else if("Alt".equals(name) && inAlt){
						inAlt=false;
						String best=alt.xDefault!=null ? alt.xDefault : (alt.en!=null ? alt.en : alt.first);
						if(best!=null){
							if("description".equals(altOwner) && dcDescription==null)
								dcDescription=best;
							else if("Caption".equals(altOwner) && caption==null)
								caption=best;
						}
						altOwner=null;
						alt=null;
					}else if("Headline".equals(name)){
						inHeadline=false;
					}
				}
			}
		}

		String headlineText=headline.toString().trim();
		String result=dcDescription!=null ? dcDescription : (!headlineText.isEmpty() ? headlineText : caption);
		return result!=null && !result.isEmpty() ? result : null;
	}

	private static class LangAlt{
		String xDefault, en, first;
	}

	/**
	 * Strips any namespace prefix so the parser works with namespace processing on
	 * (android.util.Xml default) or off (plain kxml2): "dc:description" and
	 * "description" both match.
	 */
	private static String localName(String name){
		int colon=name.indexOf(':');
		return colon>=0 ? name.substring(colon+1) : name;
	}

	/**
	 * Reads XPTitle then XPSubject from a JPEG's EXIF IFD0. androidx.exifinterface does
	 * not register the Windows XP* tags, so the TIFF directory is walked directly.
	 * Pure byte-level code — no Android APIs — so it runs in a JVM harness.
	 */
	@Nullable
	public static String readXpTitleOrSubject(byte[] jpeg){
		byte[] exif=findExifSegment(jpeg);
		if(exif==null || exif.length<8)
			return null;
		try{
			boolean little;
			if(exif[0]=='I' && exif[1]=='I')
				little=true;
			else if(exif[0]=='M' && exif[1]=='M')
				little=false;
			else
				return null;
			if(u16(exif, 2, little)!=42)
				return null;
			long ifd0=u32(exif, 4, little);
			if(ifd0<0 || ifd0+2>exif.length)
				return null;
			int count=u16(exif, (int)ifd0, little);
			String title=null, subject=null;
			for(int i=0;i<count;i++){
				int entry=(int)ifd0+2+i*12;
				if(entry+12>exif.length)
					break;
				int tag=u16(exif, entry, little);
				if(tag!=TAG_XP_TITLE && tag!=TAG_XP_SUBJECT)
					continue;
				int type=u16(exif, entry+2, little);
				if(type!=1 && type!=7) // BYTE / UNDEFINED
					continue;
				long cnt=u32(exif, entry+4, little);
				if(cnt<2 || cnt>1024)
					continue;
				long off=cnt<=4 ? entry+8 : u32(exif, entry+8, little);
				if(off<0 || off+cnt>exif.length)
					continue;
				byte[] buf=new byte[(int)cnt];
				System.arraycopy(exif, (int)off, buf, 0, buf.length);
				String text=decodeUtf16Le(buf);
				if(text==null || text.isEmpty())
					continue;
				if(tag==TAG_XP_TITLE)
					title=text;
				else
					subject=text;
			}
			return title!=null ? title : subject;
		}catch(Exception x){
			return null;
		}
	}

	/**
	 * Composer prefill normalization (spec §2): collapse newlines to single spaces, trim,
	 * truncate to MAX_DESCRIPTION_LENGTH on a word boundary. The caller runs the raw text
	 * through ComposeFragment.sanitizeMediaDescription before calling this.
	 */
	@Nullable
	public static String normalizePrefill(@Nullable String text){
		if(text==null)
			return null;
		String s=text.replaceAll("[\\r\\n]+", " ").trim();
		s=truncateOnWordBoundary(s, MAX_DESCRIPTION_LENGTH);
		return s.isEmpty() ? null : s;
	}

	public static String truncateOnWordBoundary(String s, int max){
		if(s.length()<=max)
			return s;
		// Keep the longest prefix that ends on whitespace (the word after it would not
		// fit whole), otherwise hard-cut mid-word as a last resort.
		int cut=-1;
		for(int i=max-1;i>=0;i--){
			if(Character.isWhitespace(s.charAt(i))){
				cut=i+1;
				break;
			}
		}
		if(cut<=0)
			cut=max;
		return trimTrailingWhitespace(s.substring(0, cut));
	}

	@Nullable
	private static String take(@Nullable String s){
		if(s==null)
			return null;
		s=s.trim();
		return s.isEmpty() ? null : s;
	}

	private static String trimTrailingWhitespace(String s){
		int end=s.length();
		while(end>0 && Character.isWhitespace(s.charAt(end-1)))
			end--;
		return s.substring(0, end);
	}

	@Nullable
	private static String decodeUtf16Le(byte[] data){
		// XP strings are UTF-16LE, usually NUL-terminated; tolerate odd lengths.
		// Strip NUL characters (not bytes — an ASCII char's high byte is also 0) after decode.
		int len=data.length&~1;
		if(len==0)
			return null;
		String s=new String(data, 0, len, StandardCharsets.UTF_16LE);
		int end=s.length();
		while(end>0 && s.charAt(end-1)==0)
			end--;
		s=s.substring(0, end).trim();
		return s.isEmpty() ? null : s;
	}

	/**
	 * Finds the TIFF payload ("Exif\0\0" header stripped) of the first EXIF APP1 segment.
	 */
	@Nullable
	private static byte[] findExifSegment(byte[] jpeg){
		if(jpeg.length<4 || (jpeg[0]&0xFF)!=0xFF || (jpeg[1]&0xFF)!=0xD8)
			return null;
		int pos=2;
		while(pos+4<=jpeg.length){
			if((jpeg[pos]&0xFF)!=0xFF){
				pos++;
				continue;
			}
			int marker=jpeg[pos+1]&0xFF;
			if(marker==0xDA) // start of scan — image data, metadata ends here
				break;
			if(marker==0x01 || (marker>=0xD0 && marker<=0xD8) || marker==0xFF){
				pos+=2;
				continue;
			}
			int len=((jpeg[pos+2]&0xFF)<<8)|(jpeg[pos+3]&0xFF);
			if(len<2 || pos+2+len>jpeg.length)
				return null;
			if(marker==0xE1 && len>=8
					&& jpeg[pos+4]=='E' && jpeg[pos+5]=='x' && jpeg[pos+6]=='i' && jpeg[pos+7]=='f'
					&& jpeg[pos+8]==0 && jpeg[pos+9]==0){
				byte[] payload=new byte[len-8];
				System.arraycopy(jpeg, pos+10, payload, 0, payload.length);
				return payload;
			}
			pos+=2+len;
		}
		return null;
	}

	private static int u16(byte[] b, int off, boolean little){
		if(little)
			return (b[off]&0xFF)|((b[off+1]&0xFF)<<8);
		return ((b[off]&0xFF)<<8)|(b[off+1]&0xFF);
	}

	private static long u32(byte[] b, int off, boolean little){
		if(little)
			return (b[off]&0xFFL)|((b[off+1]&0xFFL)<<8)|((b[off+2]&0xFFL)<<16)|((b[off+3]&0xFFL)<<24);
		return ((b[off]&0xFFL)<<24)|((b[off+1]&0xFFL)<<16)|((b[off+2]&0xFFL)<<8)|(b[off+3]&0xFFL);
	}

	private static byte[] readAllBytes(InputStream in) throws IOException{
		java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
		byte[] buf=new byte[8192];
		int read;
		while((read=in.read(buf))>0)
			out.write(buf, 0, read);
		return out.toByteArray();
	}
}
