package org.joinmastodon.android.ui;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * M1 regression: the end-line plural (docs/ADDRESS-LINE-SPEC.md §3) must word
 * the singular differently from the plural. A JVM test cannot call
 * getQuantityString, so this reads res/values/strings.xml directly and pins
 * the two quantity entries — the wording itself is the contract.
 */
public class ThreadEndLinePluralsTest{

	private static File stringsXml(){
		String[] candidates={
				"src/main/res/values/strings.xml",
				"mastodon/src/main/res/values/strings.xml",
				"../mastodon/src/main/res/values/strings.xml"
		};
		for(String c:candidates){
			File f=new File(c);
			if(f.isFile())
				return f;
		}
		throw new IllegalStateException("strings.xml not found from "+new File("").getAbsolutePath());
	}

	private static String pluralItem(String name, String quantity) throws Exception{
		Document doc=DocumentBuilderFactory.newInstance()
				.newDocumentBuilder()
				.parse(new FileInputStream(stringsXml()));
		NodeList plurals=doc.getElementsByTagName("plurals");
		for(int i=0;i<plurals.getLength();i++){
			Element p=(Element)plurals.item(i);
			if(!name.equals(p.getAttribute("name")))
				continue;
			NodeList items=p.getElementsByTagName("item");
			for(int j=0;j<items.getLength();j++){
				Element item=(Element)items.item(j);
				if(quantity.equals(item.getAttribute("quantity")))
					return item.getTextContent().trim();
			}
			throw new IllegalStateException("plurals "+name+" has no item with quantity "+quantity);
		}
		throw new IllegalStateException("plurals "+name+" not found in "+stringsXml());
	}

	@Test
	public void singularAndPluralEndLinesDifferInWording() throws Exception{
		// M1: with one post the line must say "post", with two "posts" — the
		// two quantity entries are different strings, not one shared template.
		String one=pluralItem("thread_end_line", "one");
		String other=pluralItem("thread_end_line", "other");
		assertNotEquals(one, other);
		assertTrue("one must read \"post\": "+one, one.contains(" post "));
		assertTrue("other must read \"posts\": "+other, other.contains(" posts "));
	}

	@Test
	public void endLineKeepsTheCountPlaceholder() throws Exception{
		// The count is the only thing that varies; the %d must survive in both.
		assertTrue(pluralItem("thread_end_line", "one").contains("%d"));
		assertTrue(pluralItem("thread_end_line", "other").contains("%d"));
	}
}
