package org.martus.server.formirroring;

import java.io.File;
import java.util.Vector;

import org.martus.common.BulletinHeaderPacket;
import org.martus.common.Database;
import org.martus.common.DatabaseKey;
import org.martus.common.FieldDataPacket;
import org.martus.common.MartusUtilities;
import org.martus.common.MockMartusSecurity;
import org.martus.common.TestCaseEnhanced;
import org.martus.common.UniversalId;
import org.martus.server.forclients.MockMartusServer;

public class TestServerForMirroring extends TestCaseEnhanced
{
	public TestServerForMirroring(String name)
	{
		super(name);
	}

	protected void setUp() throws Exception
	{
		MockMartusSecurity serverSecurity = MockMartusSecurity.createServer();
		coreServer = new MockMartusServer();
		coreServer.setSecurity(serverSecurity);
		server = new ServerForMirroring(coreServer);
		
		clientSecurity1 = MockMartusSecurity.createClient();
		clientSecurity2 = MockMartusSecurity.createOtherClient();

		bhp1 = new BulletinHeaderPacket(clientSecurity1.getPublicKeyString());
		bhp2 = new BulletinHeaderPacket(clientSecurity1.getPublicKeyString());
		bhp3 = new BulletinHeaderPacket(clientSecurity2.getPublicKeyString());
		Database db = server.getDatabase();
		bhp1.writeXmlToDatabase(db, false, clientSecurity1);
		bhp2.writeXmlToDatabase(db, false, clientSecurity1);
		bhp3.writeXmlToDatabase(db, false, clientSecurity2);
		
		UniversalId fdpUid = FieldDataPacket.createUniversalId(clientSecurity1.getPublicKeyString());
		String[] tags = {"whatever"};
		FieldDataPacket fdp1 = new FieldDataPacket(fdpUid, tags);
		fdp1.writeXmlToDatabase(db, false, clientSecurity2);
		
		UniversalId otherPacketId = UniversalId.createFromAccountAndPrefix(clientSecurity2.getPublicKeyString(), "X");
		DatabaseKey key = new DatabaseKey(otherPacketId);
		db.writeRecord(key, "Not a valid packet");
	}

	protected void tearDown() throws Exception
	{
		coreServer.deleteAllFiles();
	}
	
	public void testGetPublicInfo() throws Exception
	{
		Vector publicInfo = server.getPublicInfo();
		assertEquals(2, publicInfo.size());
		String publicKey = (String)publicInfo.get(0);
		String gotSig = (String)publicInfo.get(1);
		String serverPublicKeyString = server.getSecurity().getPublicKeyString();
		MartusUtilities.validatePublicInfo(publicKey, gotSig, clientSecurity1);
		assertEquals(serverPublicKeyString, publicInfo.get(0));
		
	}
	
	public void testIsAuthorizedForMirroring() throws Exception
	{
		MockMartusServer nobodyAuthorizedCore = new MockMartusServer();
		ServerForMirroring nobodyAuthorized = new ServerForMirroring(nobodyAuthorizedCore);
		assertFalse("client already authorized?", nobodyAuthorized.isAuthorizedForMirroring(clientSecurity1.getPublicKeyString()));
		nobodyAuthorizedCore.deleteAllFiles();
		
		MockMartusServer twoAuthorizedCore = new MockMartusServer();
		twoAuthorizedCore.enterSecureMode();
		File mirrorsWhoCallUs = new File(twoAuthorizedCore.getStartupConfigDirectory(), "mirrorsWhoCallUs");
		mirrorsWhoCallUs.mkdirs();
		File pubKeyFile1 = new File(mirrorsWhoCallUs, "code=1.2.3.4.5-ip=1.2.3.4.txt");
		MartusUtilities.exportServerPublicKey(clientSecurity1, pubKeyFile1);
		File pubKeyFile2 = new File(mirrorsWhoCallUs, "code=2.3.4.5.6-ip=2.3.4.5.txt");
		MartusUtilities.exportServerPublicKey(clientSecurity2, pubKeyFile2);
		ServerForMirroring twoAuthorized = new ServerForMirroring(twoAuthorizedCore);
		assertTrue("client1 not authorized?", twoAuthorized.isAuthorizedForMirroring(clientSecurity1.getPublicKeyString()));
		assertTrue("client2 not authorized?", twoAuthorized.isAuthorizedForMirroring(clientSecurity2.getPublicKeyString()));
		assertFalse("ourselves authorized?", twoAuthorized.isAuthorizedForMirroring(coreServer.getAccountId()));
		mirrorsWhoCallUs.delete();
		twoAuthorizedCore.deleteAllFiles();
		
	}

	public void testListAccounts() throws Exception
	{
		Vector result = server.listAccountsForMirroring();
		assertEquals(2, result.size());
		assertContains(clientSecurity1.getPublicKeyString(), result);
		assertContains(clientSecurity2.getPublicKeyString(), result);
	}
	
	public void testListBulletins() throws Exception
	{
		Vector result1 = server.listBulletinsForMirroring(clientSecurity1.getPublicKeyString());
		assertEquals(2, result1.size());
		Vector ids1 = new Vector();
		ids1.add(((Vector)result1.get(0)).get(0));
		ids1.add(((Vector)result1.get(1)).get(0));
		assertContains(bhp1.getLocalId(), ids1);
		assertContains(bhp2.getLocalId(), ids1);
		
		Vector result2 = server.listBulletinsForMirroring(clientSecurity2.getPublicKeyString());
		assertEquals(1, result2.size());
		Vector ids2 = new Vector();
		ids2.add(((Vector)result2.get(0)).get(0));
		assertContains(bhp3.getLocalId(), ids2);
	}

	ServerForMirroring server;
	MockMartusServer coreServer;

	MockMartusSecurity clientSecurity1;
	MockMartusSecurity clientSecurity2;

	BulletinHeaderPacket bhp1;
	BulletinHeaderPacket bhp2;
	BulletinHeaderPacket bhp3;
}
