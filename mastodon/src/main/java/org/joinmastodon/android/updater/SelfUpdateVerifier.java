package org.joinmastodon.android.updater;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.util.Log;

import org.joinmastodon.android.BuildConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.Nullable;
import okhttp3.HttpUrl;

/**
 * MAMMUT: Wave 2 security hardening (S-H1) — pure URL, digest, version, and APK
 * integrity/signature verification shared by self-updater variants and pinned by JVM tests.
 */
public final class SelfUpdateVerifier{
	private static final String TAG="SelfUpdateVerifier";
	private static final Pattern SEMVER_PATTERN=Pattern.compile("v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
	private static final Pattern SHA256_LINE_PATTERN=Pattern.compile("^([0-9a-fA-F]{64})(?:\\s+.*)?$");
	private static final Set<String> ALLOWED_DOWNLOAD_HOSTS=Set.of(
			"github.com",
			"objects.githubusercontent.com",
			"release-assets.githubusercontent.com",
			"github-releases.githubusercontent.com"
	);

	private SelfUpdateVerifier(){}

	/**
	 * Accepts only HTTPS URLs hosted on GitHub's official release download domains,
	 * with no embedded credentials.
	 */
	public static boolean isAllowedDownloadUrl(@Nullable String url){
		if(url==null)
			return false;
		HttpUrl parsed=HttpUrl.parse(url.trim());
		if(parsed==null || !parsed.isHttps())
			return false;
		if(!parsed.encodedUsername().isEmpty() || !parsed.encodedPassword().isEmpty())
			return false;
		String host=parsed.host().toLowerCase(Locale.US);
		return ALLOWED_DOWNLOAD_HOSTS.contains(host);
	}

	/**
	 * Parses a semantic version like {@code v1.3.16} or {@code 1.3.15-debug} into a
	 * comparable 48-bit integer, or {@code -1} if malformed.
	 */
	public static long parseSemanticVersion(@Nullable String versionText){
		if(versionText==null)
			return -1L;
		Matcher m=SEMVER_PATTERN.matcher(versionText);
		if(!m.find())
			return -1L;
		try{
			int major=Integer.parseInt(m.group(1));
			int minor=Integer.parseInt(m.group(2));
			int patch=m.group(3)!=null ? Integer.parseInt(m.group(3)) : 0;
			if(major<0 || minor<0 || patch<0 || major>0xFFFF || minor>0xFFFF || patch>0xFFFF)
				return -1L;
			return ((long) major << 32) | ((long) minor << 16) | patch;
		}catch(NumberFormatException x){
			return -1L;
		}
	}

	/**
	 * Extracts the lowercase 64-hex SHA-256 digest from a {@code sha256sum} file or bare hex string.
	 */
	@Nullable
	public static String parseSha256File(@Nullable String content){
		if(content==null)
			return null;
		String firstLine=content.trim();
		int nl=firstLine.indexOf('\n');
		if(nl>=0)
			firstLine=firstLine.substring(0, nl).trim();
		Matcher m=SHA256_LINE_PATTERN.matcher(firstLine);
		if(!m.matches())
			return null;
		return m.group(1).toLowerCase(Locale.US);
	}

	/** Constant-time comparison of two lowercase 64-char hex SHA-256 strings. */
	public static boolean digestsEqual(@Nullable String expectedHex, @Nullable String actualHex){
		if(expectedHex==null || actualHex==null)
			return false;
		byte[] a=expectedHex.toLowerCase(Locale.US).getBytes(StandardCharsets.US_ASCII);
		byte[] b=actualHex.toLowerCase(Locale.US).getBytes(StandardCharsets.US_ASCII);
		return a.length==64 && MessageDigest.isEqual(a, b);
	}

	public static String sha256Hex(File file) throws IOException{
		MessageDigest md=newSha256();
		byte[] buf=new byte[16384];
		try(InputStream in=new FileInputStream(file)){
			int read;
			while((read=in.read(buf))>0){
				md.update(buf, 0, read);
			}
		}
		return toHex(md.digest());
	}

	/**
	 * Verifies a downloaded update APK before install:
	 * 1) Optional/required SHA-256 digest match against the release's .sha256 checksum
	 * 2) Matching {@code packageName} (blocks trojans disguised under another applicationId)
	 * 3) Non-downgrade versionCode (unless {@code allowSameOrLowerVersion})
	 * 4) Identical signing certificate set to the currently installed app.
	 */
	public static boolean verifyApkFile(Context context, File apkFile, @Nullable String expectedSha256Hex, boolean requireSha256, boolean allowSameOrLowerVersion){
		if(apkFile==null || !apkFile.isFile() || apkFile.length()<=0)
			return false;
		if(requireSha256 && expectedSha256Hex==null){
			Log.w(TAG, "verifyApkFile: missing required SHA-256 digest");
			return false;
		}
		if(expectedSha256Hex!=null){
			try{
				String actual=sha256Hex(apkFile);
				if(!digestsEqual(expectedSha256Hex, actual)){
					Log.w(TAG, "verifyApkFile: SHA-256 mismatch");
					return false;
				}
			}catch(IOException x){
				Log.w(TAG, "verifyApkFile: failed hashing APK", x);
				return false;
			}
		}

		PackageManager pm=context.getPackageManager();
		int flags=Build.VERSION.SDK_INT>=Build.VERSION_CODES.P
				? PackageManager.GET_SIGNING_CERTIFICATES
				: PackageManager.GET_SIGNATURES;
		PackageInfo archiveInfo=pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), flags);
		if(archiveInfo==null){
			Log.w(TAG, "verifyApkFile: unable to parse archive info");
			return false;
		}
		if(!context.getPackageName().equals(archiveInfo.packageName)){
			Log.w(TAG, "verifyApkFile: packageName mismatch: "+archiveInfo.packageName);
			return false;
		}
		long archiveVersionCode=Build.VERSION.SDK_INT>=Build.VERSION_CODES.P
				? archiveInfo.getLongVersionCode()
				: archiveInfo.versionCode;
		if(!allowSameOrLowerVersion && archiveVersionCode<=BuildConfig.VERSION_CODE){
			Log.w(TAG, "verifyApkFile: archive versionCode "+archiveVersionCode+" is not newer than "+BuildConfig.VERSION_CODE);
			return false;
		}

		PackageInfo installedInfo;
		try{
			installedInfo=pm.getPackageInfo(context.getPackageName(), flags);
		}catch(PackageManager.NameNotFoundException x){
			Log.w(TAG, "verifyApkFile: could not query installed package", x);
			return false;
		}

		Set<String> installedCerts=extractCertDigests(installedInfo);
		Set<String> archiveCerts=extractCertDigests(archiveInfo);
		if(installedCerts.isEmpty() || !installedCerts.equals(archiveCerts)){
			Log.w(TAG, "verifyApkFile: signing certificate mismatch");
			return false;
		}
		return true;
	}

	private static Set<String> extractCertDigests(PackageInfo info){
		Set<String> out=new HashSet<>();
		if(info==null)
			return out;
		Signature[] sigs=null;
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P){
			SigningInfo si=info.signingInfo;
			if(si!=null){
				sigs=si.hasMultipleSigners() ? si.getApkContentsSigners() : si.getSigningCertificateHistory();
			}
		}
		if(sigs==null){
			@SuppressWarnings("deprecation")
			Signature[] legacy=info.signatures;
			sigs=legacy;
		}
		if(sigs==null)
			return out;
		MessageDigest md=newSha256();
		for(Signature s:sigs){
			if(s!=null)
				out.add(toHex(md.digest(s.toByteArray())));
		}
		return out;
	}

	private static MessageDigest newSha256(){
		try{
			return MessageDigest.getInstance("SHA-256");
		}catch(NoSuchAlgorithmException x){
			throw new IllegalStateException("SHA-256 unavailable", x);
		}
	}

	private static String toHex(byte[] bytes){
		StringBuilder sb=new StringBuilder(bytes.length*2);
		for(byte b:bytes){
			sb.append(Character.forDigit((b >> 4) & 0xF, 16));
			sb.append(Character.forDigit(b & 0xF, 16));
		}
		return sb.toString();
	}
}
