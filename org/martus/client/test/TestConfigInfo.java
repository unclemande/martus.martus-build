/*

The Martus(tm) free, social justice documentation and
monitoring software. Copyright (C) 2001-2003, Beneficent
Technology, Inc. (Benetech).

Martus is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either
version 2 of the License, or (at your option) any later
version with the additions and exceptions described in the
accompanying Martus license file entitled "license.txt".

It is distributed WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, including warranties of fitness of purpose or
merchantability.  See the accompanying Martus License and
GPL license for more details on the required license terms
for this software.

You should have received a copy of the GNU General Public
License along with this program; if not, write to the Free
Software Foundation, Inc., 59 Temple Place - Suite 330,
Boston, MA 02111-1307, USA.

*/

package org.martus.client.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Vector;

import org.martus.client.core.ConfigInfo;
import org.martus.common.MartusSecurity;
import org.martus.common.MartusUtilities;
import org.martus.common.TestCaseEnhanced;

public class TestConfigInfo extends TestCaseEnhanced
{
    public TestConfigInfo(String name) throws IOException
	{
        super(name);
    }

	public void testBasics()
	{
		ConfigInfo info = new ConfigInfo();
		verifyEmptyInfo(info, "constructor");

		info.setAuthor("fred");
		assertEquals("fred", info.getAuthor());
	
		info.setTemplateDetails(sampleTemplateDetails);
		assertEquals("Details not set?", sampleTemplateDetails, info.getTemplateDetails());

		info.clear();
		verifyEmptyInfo(info, "clear");
	}

	public void testHasContactInfo() throws Exception
	{
		ConfigInfo info = new ConfigInfo();
		info.setAuthor("fred");
		assertEquals("author isn't enough contact info?", true, info.hasContactInfo());
		info.setAuthor("");
		info.setOrganization("whatever");
		assertEquals("organization isn't enough contact info?", true, info.hasContactInfo());
	}

	public void testLoadVersions() throws Exception
	{
		for(short version = 1; version <= ConfigInfo.VERSION; ++version)
		{
			byte[] data = createFileWithSampleData(version);
			ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
			verifyLoadSpecificVersion(inputStream, version);
		}
	}

	public void testSaveFull() throws Exception
	{
		ConfigInfo info = new ConfigInfo();

		setConfigToSampleData(info);
		verifySampleInfo(info, "testSaveFull", ConfigInfo.VERSION);

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		info.save(outputStream);
		outputStream.close();

		ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
		verifyLoadSpecificVersion(inputStream, ConfigInfo.VERSION);
	}

	public void testSaveEmpty() throws Exception
	{
		ConfigInfo emptyInfo = new ConfigInfo();
		ByteArrayOutputStream emptyOutputStream = new ByteArrayOutputStream();
		emptyInfo.save(emptyOutputStream);
		emptyOutputStream.close();

		emptyInfo.setAuthor("should go away");
		ByteArrayInputStream emptyInputStream = new ByteArrayInputStream(emptyOutputStream.toByteArray());
		emptyInfo = ConfigInfo.load(emptyInputStream);
		assertEquals("should have cleared", "", emptyInfo.getAuthor());
	}

	public void testSaveNonEmpty() throws Exception
	{
		ConfigInfo info = new ConfigInfo();
		String server = "server";
		info.setServerName(server);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		info.save(outputStream);
		info.setServerName("should be reverted");

		ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
		info = ConfigInfo.load(inputStream);
		assertEquals("should have reverted", server, info.getServerName());
	}

	public void testRemoveHQKey() throws Exception
	{
		ConfigInfo info = new ConfigInfo();
		String hqKey = "HQKey";
		info.setHQKey(hqKey);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		info.save(outputStream);
		info.clearHQKey();
		assertEquals("HQ Key Should be cleared", "", info.getHQKey());

		ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
		info = ConfigInfo.load(inputStream);
		assertEquals("HQ key should have reverted", hqKey, info.getHQKey());
	}


	public void testGetContactInfo() throws Exception
	{
		ConfigInfo newInfo = new ConfigInfo();
		newInfo.setAuthor(sampleAuthor);
		newInfo.setAddress(sampleAddress);
		newInfo.setPhone(samplePhone);
		MartusSecurity signer = new MartusSecurity();
		signer.createKeyPair(512);
		Vector contactInfo = newInfo.getContactInfo(signer);
		assertEquals("Wrong contactinfo size", 9, contactInfo.size());
		String publicKey = (String)contactInfo.get(0);

		assertEquals("Not the publicKey?", signer.getPublicKeyString(), publicKey);
		int contentSize = ((Integer)(contactInfo.get(1))).intValue();
		assertEquals("Not the correct size?", contentSize + 3, contactInfo.size());
		assertEquals("Author not correct?", sampleAuthor, contactInfo.get(2));
		assertEquals("Address not correct?", sampleAddress, contactInfo.get(7));
		assertEquals("phone not correct?", samplePhone, contactInfo.get(6));
		String signature = (String)contactInfo.get(contactInfo.size()-1);
		contactInfo.remove(contactInfo.size()-1);
		assertTrue("Signature failed?", MartusUtilities.verifySignature(contactInfo, signer, publicKey, signature));
	}
	
	
	
	void setConfigToSampleData(ConfigInfo info)
	{
		info.setAuthor(sampleAuthor);
		info.setOrganization(sampleOrg);
		info.setEmail(sampleEmail);
		info.setWebPage(sampleWebPage);
		info.setPhone(samplePhone);
		info.setAddress(sampleAddress);
		info.setServerName(sampleServerName);
		info.setServerPublicKey(sampleServerKey);
		info.setTemplateDetails(sampleTemplateDetails);
		info.setHQKey(sampleHQKey);
		info.setSendContactInfoToServer(sampleSendContactInfoToServer);
		info.setServerCompliance(sampleServerCompliance);
	}

	void verifyEmptyInfo(ConfigInfo info, String label)
	{
		assertEquals(label + ": Full has contact info", false, info.hasContactInfo());
		assertEquals(label + ": sampleSource", "", info.getAuthor());
		assertEquals(label + ": sampleOrg", "", info.getOrganization());
		assertEquals(label + ": sampleEmail", "", info.getEmail());
		assertEquals(label + ": sampleWebPage", "", info.getWebPage());
		assertEquals(label + ": samplePhone", "", info.getPhone());
		assertEquals(label + ": sampleAddress", "", info.getAddress());
		assertEquals(label + ": sampleServerName", "", info.getServerName());
		assertEquals(label + ": sampleServerKey", "", info.getServerPublicKey());
		assertEquals(label + ": sampleTemplateDetails", "", info.getTemplateDetails());
		assertEquals(label + ": sampleHQKey", "", info.getHQKey());
		assertEquals(label + ": sampleSendContactInfoToServer", false, info.shouldContactInfoBeSentToServer());
		assertEquals(label + ": sampleServerComplicance", "", info.getServerCompliance());

	}

	void verifySampleInfo(ConfigInfo info, String label, int VERSION)
	{
		assertEquals(label + ": Full has contact info", true, info.hasContactInfo());
		assertEquals(label + ": sampleSource", sampleAuthor, info.getAuthor());
		assertEquals(label + ": sampleOrg", sampleOrg, info.getOrganization());
		assertEquals(label + ": sampleEmail", sampleEmail, info.getEmail());
		assertEquals(label + ": sampleWebPage", sampleWebPage, info.getWebPage());
		assertEquals(label + ": samplePhone", samplePhone, info.getPhone());
		assertEquals(label + ": sampleAddress", sampleAddress, info.getAddress());
		assertEquals(label + ": sampleServerName", sampleServerName, info.getServerName());
		assertEquals(label + ": sampleServerKey", sampleServerKey, info.getServerPublicKey());
		assertEquals(label + ": sampleTemplateDetails", sampleTemplateDetails, info.getTemplateDetails());
		assertEquals(label + ": sampleHQKey", sampleHQKey, info.getHQKey());
		if(VERSION >= 2)
			assertEquals(label + ": sampleSendContactInfoToServer", sampleSendContactInfoToServer, info.shouldContactInfoBeSentToServer());
		else
			assertEquals(label + ": sampleSendContactInfoToServer", false, info.shouldContactInfoBeSentToServer());
		if(VERSION >= 3)
			; // Version 3 added no data fields
		else
			; // Version 3 added no data fields
		if(VERSION >= 4)
			assertEquals(label + ": sampleServerComplicance", sampleServerCompliance, info.getServerCompliance());
		else
			assertEquals(label + ": sampleServerComplicance", "", info.getServerCompliance());
	}

	void verifyLoadSpecificVersion(ByteArrayInputStream inputStream, short VERSION)
	{
		ConfigInfo info = new ConfigInfo();
		info = ConfigInfo.load(inputStream);
		verifySampleInfo(info, "testLoadVersion" + VERSION, VERSION);
	}

	public byte[] createFileWithSampleData(short VERSION)
		throws IOException
	{
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(outputStream);
		out.writeShort(VERSION);
		DataOutputStream out1 = out;
		
		out1.writeUTF(sampleAuthor);
		out1.writeUTF(sampleOrg);
		out1.writeUTF(sampleEmail);
		out1.writeUTF(sampleWebPage);
		out1.writeUTF(samplePhone);
		out1.writeUTF(sampleAddress);
		out1.writeUTF(sampleServerName);
		out1.writeUTF(sampleTemplateDetails);
		out1.writeUTF(sampleHQKey);
		out1.writeUTF(sampleServerKey);
		if(VERSION >= 2)
		{
			DataOutputStream out2 = out;
			out2.writeBoolean(sampleSendContactInfoToServer);
		}
		if(VERSION >= 3)
			; // Version 3 added no data fields
		if(VERSION >= 4)
		{
			DataOutputStream out2 = out;
			out2.writeUTF(sampleServerCompliance);
		}
		out.close();
		return outputStream.toByteArray();
	}

//Version 1
	final String sampleAuthor = "author";
	final String sampleOrg = "org";
	final String sampleEmail = "email";
	final String sampleWebPage = "web";
	final String samplePhone = "phone";
	final String sampleAddress = "address\nline2";
	final String sampleServerName = "server name";
	final String sampleServerKey = "server pub key";
	final String sampleTemplateDetails = "details\ndetail2";
	final String sampleHQKey = "1234324234";
//Version 2
	final boolean sampleSendContactInfoToServer = true;
//Version 3
	//nothing added just signed.
//Version 4
	final String sampleServerCompliance = "I am compliant";
}
