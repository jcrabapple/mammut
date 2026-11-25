package org.joinmastodon.android;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for Fix 6 of the 1.3.5 audit (docs/AUDIT-1.3.5-TRIAGE.md §Fix 6),
 * MUST S1.4 (release trust anchors carry no user CAs; debug-overrides do) and
 * LOW S1.6 (backup rules exclude accounts.db and the account registry).
 */
public class SecurityConfigFilesTest{
	private static String readResXml(String name) throws IOException{
		String[] candidates={
				"src/main/res/xml/"+name,
				"mastodon/src/main/res/xml/"+name,
				"../mastodon/src/main/res/xml/"+name,
		};
		for(String candidate : candidates){
			Path p=Paths.get(System.getProperty("user.dir", ".")).resolve(candidate);
			if(Files.exists(p))
				return new String(Files.readAllBytes(p), java.nio.charset.StandardCharsets.UTF_8);
		}
		throw new IOException("could not locate res/xml/"+name+" from user.dir="+System.getProperty("user.dir"));
	}

	private static String baseConfigSection(String xml){
		int start=xml.indexOf("<base-config");
		int end=xml.indexOf("</base-config>");
		assertTrue("base-config present", start>=0 && end>start);
		return xml.substring(start, end);
	}

	@Test
	public void releaseBaseConfigHasNoUserTrustAnchor() throws IOException{
		String xml=readResXml("network_security_config.xml");
		String base=baseConfigSection(xml);
		assertTrue("release base-config must disable cleartext HTTP for API 23-27",
				base.contains("cleartextTrafficPermitted=\"false\""));
		assertFalse("user CAs must not be in the release base trust anchors",
				base.contains("<certificates src=\"user\""));
		assertTrue(base.contains("<certificates src=\"system\""));
	}

	@Test
	public void debugOverridesKeepUserCasForProxyDebugging() throws IOException{
		String xml=readResXml("network_security_config.xml");
		int start=xml.indexOf("<debug-overrides");
		int end=xml.indexOf("</debug-overrides>");
		assertTrue("debug-overrides present", start>=0 && end>start);
		String debug=xml.substring(start, end);
		assertTrue(debug.contains("<certificates src=\"user\""));
	}

	@Test
	public void legacyBackupRulesExcludeCredentialStores() throws IOException{
		String xml=readResXml("backup_rules.xml");
		assertTrue(xml.contains("<exclude domain=\"database\" path=\"accounts.db\""));
		assertTrue(xml.contains("<exclude domain=\"sharedpref\" path=\"account_manager.xml\""));
	}

	@Test
	public void dataExtractionRulesExcludeCredentialStores() throws IOException{
		String xml=readResXml("data_extraction_rules.xml");
		assertTrue(xml.contains("<cloud-backup>"));
		assertTrue(xml.contains("<device-transfer>"));
		assertTrue(xml.contains("<exclude domain=\"database\" path=\"accounts.db\""));
		assertTrue(xml.contains("<exclude domain=\"sharedpref\" path=\"account_manager.xml\""));
		assertTrue(xml.contains("<exclude domain=\"database\" path=\".\""));
		assertTrue(xml.contains("<exclude domain=\"sharedpref\" path=\".\""));
		assertTrue(xml.contains("<exclude domain=\"file\" path=\".\""));
	}

	@Test
	public void manifestDisablesCloudBackup() throws IOException{
		String[] candidates={
				"src/main/AndroidManifest.xml",
				"mastodon/src/main/AndroidManifest.xml",
				"../mastodon/src/main/AndroidManifest.xml",
		};
		String manifest=null;
		for(String candidate : candidates){
			Path p=Paths.get(System.getProperty("user.dir", ".")).resolve(candidate);
			if(Files.exists(p)){
				manifest=new String(Files.readAllBytes(p), java.nio.charset.StandardCharsets.UTF_8);
				break;
			}
		}
		assertTrue("AndroidManifest.xml found", manifest!=null);
		assertTrue("android:allowBackup must be false", manifest.contains("android:allowBackup=\"false\""));
		assertFalse("android:allowBackup must not be true", manifest.contains("android:allowBackup=\"true\""));
	}
}
