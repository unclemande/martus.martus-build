package org.martus.server;

import java.util.Vector;

import org.martus.common.MartusUtilities;
import org.martus.common.MockMartusSecurity;
import org.martus.common.NetworkInterfaceConstants;
import org.martus.common.TestCaseEnhanced;


public class TestServerSideNetworkHandler extends TestCaseEnhanced 
{
	public TestServerSideNetworkHandler(String name) 
	{
		super(name);
	}
	
	public void setUp() throws Exception
	{
		mySecurity = new MockMartusSecurity();
		mySecurity.createKeyPair();
		
		mockServer = new MockMartusServer();
		mockServer.initialize();
		mockServer.setSecurity(mySecurity);
		handler = new ServerSideNetworkHandler(mockServer);

		otherSecurity = new MockMartusSecurity();
		otherSecurity.createKeyPair();
	}
	
	public void tearDown() throws Exception
	{
		mockServer.deleteAllFiles();
	}

	public void testSigs() throws Exception
	{
		String myAccountId = mySecurity.getPublicKeyString();
		Vector parameters = new Vector();
		parameters.add("abc");
		parameters.add(new Integer(2));
		String badSig = "123";
		String wrongSig = MartusUtilities.sign(parameters, otherSecurity);
		
		{
			Vector badSigResult = handler.getUploadRights(myAccountId, parameters, badSig);
			assertEquals("getUploadRights badSig length", 1, badSigResult.size());
			assertEquals("getUploadRights badSig error", NetworkInterfaceConstants.SIG_ERROR, badSigResult.get(0));

			Vector wrongSigResult = handler.getUploadRights(myAccountId, parameters, badSig);
			assertEquals("getUploadRights wrongSig length", 1, wrongSigResult.size());
			assertEquals("getUploadRights wrongSig error", NetworkInterfaceConstants.SIG_ERROR, wrongSigResult.get(0));
		}

		{
			Vector badSigResult = handler.getSealedBulletinIds(myAccountId, parameters, badSig);
			assertEquals("getSealedBulletinIds badSig length", 1, badSigResult.size());
			assertEquals("getSealedBulletinIds badSig error", NetworkInterfaceConstants.SIG_ERROR, badSigResult.get(0));

			Vector wrongSigResult = handler.getSealedBulletinIds(myAccountId, parameters, badSig);
			assertEquals("getSealedBulletinIds wrongSig length", 1, wrongSigResult.size());
			assertEquals("getSealedBulletinIds wrongSig error", NetworkInterfaceConstants.SIG_ERROR, wrongSigResult.get(0));
		}

		{
			Vector badSigResult = handler.getFieldOfficeAccountIds(myAccountId, parameters, badSig);
			assertEquals("getFieldOfficeAccountIds badSig length", 1, badSigResult.size());
			assertEquals("getFieldOfficeAccountIds badSig error", NetworkInterfaceConstants.SIG_ERROR, badSigResult.get(0));

			Vector wrongSigResult = handler.getFieldOfficeAccountIds(myAccountId, parameters, badSig);
			assertEquals("getFieldOfficeAccountIds wrongSig length", 1, wrongSigResult.size());
			assertEquals("getFieldOfficeAccountIds wrongSig error", NetworkInterfaceConstants.SIG_ERROR, wrongSigResult.get(0));
		}

		{
			Vector badSigResult = handler.putBulletinChunk(myAccountId, parameters, badSig);
			assertEquals("putBulletinChunk badSig length", 1, badSigResult.size());
			assertEquals("putBulletinChunk badSig error", NetworkInterfaceConstants.SIG_ERROR, badSigResult.get(0));

			Vector wrongSigResult = handler.putBulletinChunk(myAccountId, parameters, badSig);
			assertEquals("putBulletinChunk wrongSig length", 1, wrongSigResult.size());
			assertEquals("putBulletinChunk wrongSig error", NetworkInterfaceConstants.SIG_ERROR, wrongSigResult.get(0));
		}

		{
			Vector badSigResult = handler.getBulletinChunk(myAccountId, parameters, badSig);
			assertEquals("getBulletinChunk badSig length", 1, badSigResult.size());
			assertEquals("getBulletinChunk badSig error", NetworkInterfaceConstants.SIG_ERROR, badSigResult.get(0));

			Vector wrongSigResult = handler.getBulletinChunk(myAccountId, parameters, badSig);
			assertEquals("getBulletinChunk wrongSig length", 1, wrongSigResult.size());
			assertEquals("getBulletinChunk wrongSig error", NetworkInterfaceConstants.SIG_ERROR, wrongSigResult.get(0));
		}

		{
			Vector badSigResult = handler.getPacket(myAccountId, parameters, badSig);
			assertEquals("getPacket badSig length", 1, badSigResult.size());
			assertEquals("getPacket badSig error", NetworkInterfaceConstants.SIG_ERROR, badSigResult.get(0));

			Vector wrongSigResult = handler.getPacket(myAccountId, parameters, badSig);
			assertEquals("getPacket wrongSig length", 1, wrongSigResult.size());
			assertEquals("getPacket wrongSig error", NetworkInterfaceConstants.SIG_ERROR, wrongSigResult.get(0));
		}

	}

	
	ServerSideNetworkHandler handler;
	MockMartusServer mockServer;
	MockMartusSecurity mySecurity;
	MockMartusSecurity otherSecurity;
}
