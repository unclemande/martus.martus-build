package org.martus.meta;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Date;
import java.util.Vector;

import org.martus.client.Bulletin;
import org.martus.client.BulletinFolder;
import org.martus.client.BulletinStore;
import org.martus.client.ChoiceItem;
import org.martus.client.ClientSideNetworkGateway;
import org.martus.client.ConfigInfo;
import org.martus.client.MartusApp;
import org.martus.client.MockBulletin;
import org.martus.client.MockMartusApp;
import org.martus.client.Retriever;
import org.martus.common.Base64;
import org.martus.common.BulletinHeaderPacket;
import org.martus.common.FieldDataPacket;
import org.martus.common.MartusCrypto;
import org.martus.common.MartusSecurity;
import org.martus.common.MartusUtilities;
import org.martus.common.MockMartusSecurity;
import org.martus.common.NetworkInterface;
import org.martus.common.NetworkInterfaceConstants;
import org.martus.common.NetworkInterfaceForNonSSL;
import org.martus.common.NetworkResponse;
import org.martus.common.TestCaseEnhanced;
import org.martus.common.UnicodeReader;
import org.martus.common.UnicodeWriter;
import org.martus.common.UniversalId;
import org.martus.common.MartusCrypto.MartusSignatureException;
import org.martus.common.Packet.WrongAccountException;
import org.martus.server.MartusServer;
import org.martus.server.MockMartusServer;
import org.martus.server.ServerSideNetworkHandler;
import org.martus.server.ServerSideNetworkHandlerForNonSSL;

public class TestMartusApp extends TestCaseEnhanced
{
	private final boolean VERBOSE = false;

	static final String userName = "testuser";
	static final String userName2 = "testuse!";
	static final String userPassword = "12345";
	static final String userPassword2 = "12347";
	static final String sampleMagicWord = "beans!";

    public TestMartusApp(String name) throws Exception
    {
        super(name);
	}

    public void setUp() throws Exception
    {
		TRACE_BEGIN("setUp");
		
		if(mockSecurityForApp == null)
			mockSecurityForApp = new MockMartusSecurity();
		
		if(mockSecurityForServer == null)
			mockSecurityForServer = new MockMartusSecurity();

		mockServer = new MockMartusServer();
		mockServer.initialize();
		mockServer.setSecurity(mockSecurityForServer);
		mockNonSSLServerHandler = new ServerSideNetworkHandlerForNonSSL(mockServer);
		mockSSLServerHandler = new MockServerInterfaceHandler(mockServer);

		appWithoutServer = MockMartusApp.create(mockSecurityForApp);
		MockServerNotAvailable mockServerNotAvailable = new MockServerNotAvailable();
		appWithoutServer.setSSLNetworkInterfaceHandlerForTesting(new ServerSideNetworkHandler(mockServerNotAvailable));

		appWithServer = MockMartusApp.create(mockSecurityForApp);
		appWithServer.setServerInfo("mock", mockServer.getAccountId());
		appWithServer.setSSLNetworkInterfaceHandlerForTesting(mockSSLServerHandler);
		
		appWithAccount = MockMartusApp.create(mockSecurityForApp);
		appWithAccount.setServerInfo("mock", mockServer.getAccountId());
		appWithAccount.setSSLNetworkInterfaceHandlerForTesting(mockSSLServerHandler);

		File keyPairFile = appWithAccount.getKeyPairFile();
		keyPairFile.delete();
		appWithAccount.getUploadInfoFile().delete();
		new File(appWithAccount.getConfigInfoFilename()).delete();
		new File(appWithAccount.getConfigInfoSignatureFilename()).delete();
		
		mockServer.deleteAllData();
		mockServerNotAvailable.deleteAllFiles();

		TRACE_END();
    }

    public void tearDown() throws Exception
    {
		mockServer.deleteAllFiles();

		appWithoutServer.deleteAllFiles();
		appWithServer.deleteAllFiles();
		appWithAccount.deleteAllFiles();
	}

	public void testBasics()
	{
		TRACE_BEGIN("testBasics");

		appWithServer.loadSampleData();
		BulletinStore store = appWithServer.getStore();
		assertNotNull("BulletinStore", store);
		assertTrue("Need some samples", store.getBulletinCount() > 1);
		TRACE_END();
	}
	
	public void testDbInitializerExceptionForMissingAccountMap() throws Exception
	{
		File fakeDataDirectory = null;
		File packetDirectory = null;
		File subdirectory = null;

		try
		{
			fakeDataDirectory = File.createTempFile("$$$MartusTestApp", null);
			fakeDataDirectory.deleteOnExit();
			fakeDataDirectory.delete();
			fakeDataDirectory.mkdir();
			
			packetDirectory = new File(fakeDataDirectory, "packets");
			subdirectory = new File(packetDirectory, "anyfolder");
			subdirectory.mkdirs();
			
			try
			{
				MartusApp app = new MartusApp(mockSecurityForApp, fakeDataDirectory);
				app.doAfterSigninInitalization();
				fail("Should have thrown because map is missing");
			}
			catch(MartusApp.MartusAppInitializationException expectedException)
			{
				assertEquals("wrong message?", "ErrorMissingAccountMap", expectedException.getMessage());
			}
		}
		finally
		{
			if(subdirectory != null)
				subdirectory.delete();
			if(packetDirectory != null)
				packetDirectory.delete();
			if(fakeDataDirectory != null)
			{
				new File(fakeDataDirectory, "MartusConfig.dat").delete();
				new File(fakeDataDirectory, "MartusConfig.sig").delete();
				fakeDataDirectory.delete();
			}
		}
	}
	
	public void testDbInitializerExceptionForMissingAccountMapSignature() throws Exception
	{
		File fakeDataDirectory = null;
		File packetDirectory = null;
		File subdirectory = null;
		File acctMap = null;

		try
		{
			fakeDataDirectory = File.createTempFile("$$$MartusTestApp", null);
			fakeDataDirectory.deleteOnExit();
			fakeDataDirectory.delete();
			fakeDataDirectory.mkdir();
			
			packetDirectory = new File(fakeDataDirectory, "packets");
			subdirectory = new File(packetDirectory, "anyfolder");
			subdirectory.mkdirs();
			
			acctMap = new File(packetDirectory,"acctmap.txt");
			acctMap.deleteOnExit();

			FileOutputStream out = new FileOutputStream(acctMap.getPath(), true);
			UnicodeWriter writer = new UnicodeWriter(out);
			writer.writeln("noacct=123456789");
			writer.flush();
			out.flush();
			writer.close();
			
			try
			{
				MartusApp app = new MartusApp(mockSecurityForApp, fakeDataDirectory);
				app.doAfterSigninInitalization();
			}
			catch(MartusApp.MartusAppInitializationException unExpectedException)
			{
				fail("Should not have thrown because of missing map signature");
			}
		}
		finally
		{
			if(acctMap != null )
				acctMap.delete();
			if(subdirectory != null)
				subdirectory.delete();
			if(packetDirectory != null)
				packetDirectory.delete();
			if(fakeDataDirectory != null)
			{
				new File(fakeDataDirectory, "MartusConfig.dat").delete();
				new File(fakeDataDirectory, "MartusConfig.sig").delete();
				fakeDataDirectory.delete();
			}
		}
	}
	
	public void testDbInitializerExceptionForInvalidAccountMapSignature() throws Exception
	{
		File fakeDataDirectory = null;
		File packetDirectory = null;
		File subdirectory = null;
		File acctMap = null;
		File signatureFile = null;

		try
		{
			fakeDataDirectory = File.createTempFile("$$$MartusTestApp", null);
			fakeDataDirectory.deleteOnExit();
			fakeDataDirectory.delete();
			fakeDataDirectory.mkdir();
			
			packetDirectory = new File(fakeDataDirectory, "packets");
			subdirectory = new File(packetDirectory, "anyfolder");
			subdirectory.mkdirs();
			
			acctMap = new File(packetDirectory,"acctmap.txt");
			acctMap.deleteOnExit();

			UnicodeWriter writer = new UnicodeWriter(acctMap);
			writer.writeln("noacct=123456789");
			writer.flush();
			writer.close();
			
			signatureFile = new File(packetDirectory,"acctmap.txt.sig");
			signatureFile.deleteOnExit();
			
			writer = new UnicodeWriter(signatureFile);
			writer.writeln("a fake signature");
			writer.flush();
			writer.close();
			
			try
			{
				MartusApp app = new MartusApp(mockSecurityForApp, fakeDataDirectory);
				app.doAfterSigninInitalization();
			}
			catch(MartusApp.MartusAppInitializationException unExpectedException)
			{
				fail("Should not have thrown because of invalid map signature");
			}
		}
		finally
		{
			if(acctMap != null )
				acctMap.delete();
			if(signatureFile != null)
				signatureFile.delete();
			if(subdirectory != null)
				subdirectory.delete();
			if(packetDirectory != null)
				packetDirectory.delete();
			if(fakeDataDirectory != null)
			{
				new File(fakeDataDirectory, "MartusConfig.dat").delete();
				new File(fakeDataDirectory, "MartusConfig.sig").delete();
				fakeDataDirectory.delete();
			}
		}
	}
	
	public void testGetClientId()
	{
		TRACE_BEGIN("testGetClientId");
		String securityAccount = mockSecurityForApp.getPublicKeyString();
		String appAccount = appWithServer.getAccountId();
		assertEquals("mock account wrong?", securityAccount, appAccount);
		Bulletin b = appWithAccount.createBulletin();
		assertEquals("client id wrong?", b.getAccount(), appWithAccount.getAccountId());
		TRACE_END();
	}

	public void testGetNewsFromServer() throws Exception
	{
		Vector noServerResult = appWithoutServer.getNewsFromServer();
		assertEquals(0, noServerResult.size());
		
		Vector noNews = new Vector();
		noNews.add(NetworkInterfaceConstants.OK);
		noNews.add(new Vector());
		mockServer.newsResponse = noNews;
		Vector noNewsResponse = appWithServer.getNewsFromServer();
		assertEquals(0, noNewsResponse.size());
			
		Vector badNews = new Vector();
		badNews.add("Bad Response");
		Vector badNewsItems = new Vector();
		badNewsItems.add("news for you NOT");
		badNews.add(badNewsItems);
		mockServer.newsResponse = badNews;
		Vector badNewsResponse = appWithServer.getNewsFromServer();
		assertEquals(0, badNewsResponse.size());

		final String firstNewsItem = "first news item";
		final String secondNewsItem = "second news item";
		Vector twoNews = new Vector();
		twoNews.add(NetworkInterfaceConstants.OK);
		Vector twoNewsItems = new Vector();
		twoNewsItems.add(firstNewsItem);
		twoNewsItems.add(secondNewsItem);
		twoNews.add(twoNewsItems);
		mockServer.newsResponse = twoNews;
		
		Vector twoNewsResponse = appWithServer.getNewsFromServer();
		assertEquals(2, twoNewsResponse.size());
		assertEquals(firstNewsItem, twoNewsResponse.get(0));
		assertEquals(secondNewsItem, twoNewsResponse.get(1));

	}


	public void testConfigInfo() throws Exception
	{
		TRACE_BEGIN("testConfigInfo");

		File file = new File(appWithAccount.getConfigInfoFilename());
		file.delete();
		assertEquals("delete didn't work", false, file.exists());
		appWithAccount.loadConfigInfo();

		ConfigInfo originalInfo = appWithAccount.getConfigInfo();
		assertEquals("should be empty", "", originalInfo.getAuthor());

		originalInfo.setAuthor("blah");
		assertEquals("should have been set", "blah", appWithAccount.getConfigInfo().getAuthor());
		appWithAccount.saveConfigInfo();
		assertEquals("should still be there", "blah", appWithAccount.getConfigInfo().getAuthor());
		assertEquals("save didn't work!", true, file.exists());

		originalInfo.setAuthor("something else");
		appWithAccount.loadConfigInfo();
		assertNotNull("ConfigInfo null", appWithAccount.getConfigInfo());
		assertEquals("should have reloaded", "blah", appWithAccount.getConfigInfo().getAuthor());

		File sigFile = new File(appWithAccount.getConfigInfoSignatureFilename());
		sigFile.delete();
		appWithAccount.saveConfigInfo();
		assertTrue("Missing Signature file", sigFile.exists());
		appWithAccount.loadConfigInfo();
		assertEquals("blah", appWithAccount.getConfigInfo().getAuthor());
		sigFile.delete();
		try
		{
			appWithAccount.loadConfigInfo();
			fail("Should not have verified");
		}
		catch (MartusApp.LoadConfigInfoException e)
		{
			//Expected
		}
		assertEquals("", appWithAccount.getConfigInfo().getAuthor());

		TRACE_END();

	}
	
	public void testSetAndGetHQKey() throws Exception
	{
		File configFile = new File(appWithAccount.getConfigInfoFilename());
		configFile.deleteOnExit();
		assertEquals("already exists?", false, configFile.exists());
		String sampleHQKey = "abc123";
		appWithAccount.setHQKey(sampleHQKey);
		assertEquals("Incorrect public key", sampleHQKey, appWithAccount.getHQKey());
		assertEquals("Didn't save?", true, configFile.exists());
	}

	public void testClearHQKey() throws Exception
	{
		File configFile = new File(appWithAccount.getConfigInfoFilename());
		configFile.deleteOnExit();
		assertEquals("already exists?", false, configFile.exists());
		appWithAccount.clearHQKey();
		assertEquals("HQ key exists?", "", appWithAccount.getHQKey());
		assertEquals("Didn't save?", true, configFile.exists());

		String sampleHQKey = "abc123";
		appWithAccount.setHQKey(sampleHQKey);
		assertEquals("Incorrect public key", sampleHQKey, appWithAccount.getHQKey());
		appWithAccount.clearHQKey();
		assertEquals("HQ not cleared", "", appWithAccount.getHQKey());
	}

	public void testGetCombinedPassPhrase()
	{
		String combined1 = appWithServer.getCombinedPassPhrase(userName, userPassword);
		String combined2 = appWithServer.getCombinedPassPhrase(userName2, userPassword);
		String combined3 = appWithServer.getCombinedPassPhrase(userName, userPassword2);
		assertNotEquals("username diff", combined1, combined2);
		assertNotEquals("password diff", combined1, combined3);

		String ab_c = appWithServer.getCombinedPassPhrase("ab", "c");
		String a_bc = appWithServer.getCombinedPassPhrase("a", "bc");
		assertNotEquals("abc diff", ab_c, a_bc);
	}

	public void testAttemptSignInBadKeyPairFile() throws Exception
	{
		TRACE_BEGIN("testAttemptSignInBadKeyPairFile");

		File badFile = new File(BAD_FILENAME);
		assertEquals("bad file", false, appWithServer.attemptSignInInternal(badFile, userName, userPassword));
		assertEquals("keypair not cleared?", false, mockSecurityForApp.hasKeyPair());
		assertEquals("non-blank username?", "", appWithServer.getUserName());
		appWithServer.security.createKeyPair();
		TRACE_END();
	}
	
	public void testAttemptSignInAuthorizationFailure() throws Exception
	{
		TRACE_BEGIN("testAttemptSignInAuthorizationFailure");
		
		mockSecurityForApp.fakeAuthorizationFailure = true;
		assertEquals("should fai1", false, appWithAccount.attemptSignIn(userName, userPassword));
		assertEquals("keypair not cleared?", false, mockSecurityForApp.hasKeyPair());
		mockSecurityForApp.fakeAuthorizationFailure = false;
		appWithServer.security.createKeyPair();
		
		TRACE_END();
	}
	
	public void testAttemptSignInKeyPairVersionFailure() throws Exception
	{
		TRACE_BEGIN("testAttemptSignInKeyPairVersionFailure");
		
		mockSecurityForApp.fakeKeyPairVersionFailure = true;
		assertEquals("should fail2", false, appWithAccount.attemptSignIn(userName, userPassword));
		assertEquals("keypair not cleared?", false, mockSecurityForApp.hasKeyPair());
		mockSecurityForApp.fakeKeyPairVersionFailure = false;
		appWithServer.security.createKeyPair();
		
		TRACE_END();
	}

	public void testDoesAccountExist() throws Exception
	{
		TRACE_BEGIN("testDoesAccountExist");

		File keyPairFile = appWithAccount.getKeyPairFile();
		keyPairFile.delete();
		assertEquals("account exists without a file?", false, appWithAccount.doesAccountExist());

		FileOutputStream out = new FileOutputStream(keyPairFile);
		out.write(0);
		out.close();
		
		assertEquals("account doesn't exist with a file?", true, appWithAccount.doesAccountExist());

		keyPairFile.delete();

		TRACE_END();
	}


	public void testCreateBulletin() throws Exception
	{
		TRACE_BEGIN("testCreateBulletin");
		mockSecurityForApp.loadSampleAccount();
		ConfigInfo info = appWithServer.getConfigInfo();
		String source = "who?";
		String organization = "those guys";
		String template = "Was there a bomb?";
		info.setAuthor(source);
		info.setOrganization(organization);
		info.setTemplateDetails(template);
		Bulletin b = appWithServer.createBulletin();
		assertNotNull("null Bulletin", b);
		assertEquals(source, b.get(Bulletin.TAGAUTHOR));
		assertEquals(organization, b.get(Bulletin.TAGORGANIZATION));
		assertEquals(template, b.get(Bulletin.TAGPUBLICINFO));
		assertEquals(Bulletin.STATUSDRAFT, b.getStatus());
		assertEquals("not automatically private?", true, b.isAllPrivate());
		TRACE_END();
	}

	public void testLoadBulletins() throws Exception
	{
		TRACE_BEGIN("testLoadBulletins");
		appWithServer.loadSampleData();		
		mockSecurityForApp.loadSampleAccount();

		BulletinStore store = appWithServer.getStore();
		int sampleCount = store.getBulletinCount(); 
		assertTrue("Should start with samples", sampleCount > 0);
		store.deleteAllData();
		assertEquals("Should have deleted samples", 0, store.getBulletinCount());
		appWithServer.loadFolders();
		appWithServer.loadSampleData();
		assertEquals("Should have loaded samples", sampleCount, store.getBulletinCount());
		BulletinFolder sent = store.getFolderSent();
		assertEquals("Sent should have bulletins", sampleCount, sent.getBulletinCount());

		store = appWithServer.getStore();
		appWithServer.loadFolders();
		assertEquals("Should have re-loaded samples", sampleCount, store.getBulletinCount());
		sent = store.getFolderSent();
		assertEquals("Sent should again have bulletins", sampleCount, sent.getBulletinCount());
		TRACE_END();
	}

	public void testSearch()
	{
		TRACE_BEGIN("testSearch");
		appWithServer.loadSampleData();
		BulletinStore store = appWithServer.getStore();
		String startDate = "1900-01-01";
		String endDate = "2099-12-31";
		assertNull("Search results already exists?", store.findFolder(store.getSearchFolderName()));

		Bulletin b = store.findBulletinByUniversalId((UniversalId)store.getAllBulletinUids().get(0));
		appWithServer.search(b.get("title"), startDate, endDate);
		assertNotNull("Search results should have been created", store.getSearchFolderName());

		appWithServer.search("--not in any bulletin--", startDate, endDate);
		assertEquals("search should clear results folder", 0, store.findFolder(store.getSearchFolderName()).getBulletinCount());

		assertTrue("not enough bulletins?", appWithServer.getStore().getBulletinCount() >= 5);
		assertTrue("too many bulletins?", appWithServer.getStore().getBulletinCount() <= 15);
		appWithServer.search(b.get("author"), startDate, endDate);
		assertEquals(1, store.findFolder(store.getSearchFolderName()).getBulletinCount());
		appWithServer.search(b.get(""), startDate, endDate);
		assertEquals(10, store.findFolder(store.getSearchFolderName()).getBulletinCount());

		startDate = "1999-01-19";
		endDate = startDate;
		appWithServer.search(b.get(""), startDate, endDate);
		assertEquals(1, store.findFolder(store.getSearchFolderName()).getBulletinCount());
		
		TRACE_END();
	}

	public void testFindBulletinInAllFolders() throws Exception
	{
		TRACE_BEGIN("testFindBulletinInAllFolders");
		MockMartusApp app = MockMartusApp.create();
		Bulletin b1 = app.createBulletin();
		Bulletin b2 = app.createBulletin();
		b1.save();
		b2.save();

		assertEquals("Found the bulletin already in a folder?", 0, app.findBulletinInAllVisibleFolders(b1).size());
		BulletinFolder f1 = app.createUniqueFolder();
		BulletinFolder f2 = app.createUniqueFolder();
		BulletinFolder f3 = app.createUniqueFolder();
		BulletinFolder f4 = app.getFolderDraftOutbox();
		f1.add(b1);
		f2.add(b2);
		f3.add(b1);
		f3.add(b2);
		f4.add(b2);
		BulletinFolder discarded = app.getFolderDiscarded();
		discarded.add(b2);

		Vector v1 = app.findBulletinInAllVisibleFolders(b1);
		Vector v2 = app.findBulletinInAllVisibleFolders(b2);
		assertEquals("Wrong # of folders for b1?", 2, v1.size());
		assertEquals("Wrong # of folders for b2?", 3, v2.size());
		assertTrue("Doesn't contain f1 for bulletin b1?", v1.contains(f1));
		assertEquals("Does contain f2 for bulletin b1?", false, v1.contains(f2));
		assertTrue("Doesn't contain f3 for bulletin b1?", v1.contains(f3));
		assertEquals("Does contain Discarded for bulletin b1?",false, v1.contains(discarded));

		assertEquals("Does contain f1 for bulletin b2?", false, v2.contains(f1));
		assertTrue("Doesn't contain f2 for bulletin b2?", v2.contains(f2));
		assertTrue("Doesn't contain f3 for bulletin b2?", v2.contains(f3));
		assertTrue("Doesn't contain Discarded for bulletin b2?", v2.contains(discarded));
		TRACE_END();
	}


	public void testSetServerInfo() throws Exception
	{
		final String server1 = "Server1";
		final String server2 = "Server2";
		final String key1 = "ServerKey1";
		final String key2 = "ServerKey2";
		
		MockMartusApp app = MockMartusApp.create();
		app.security =mockSecurityForApp;
		app.setServerInfo(server1, key1);
		assertEquals("Didn't set Configinfo name", server1, app.getConfigInfo().getServerName());
		assertEquals("Didn't set Configinfo key", key1, app.getConfigInfo().getServerPublicKey());
		assertNull("Should have cleared handler", app.currentNetworkInterfaceHandler);
		assertNull("Should have cleared gateway", app.currentNetworkInterfaceGateway);

		app.getCurrentNetworkInterfaceGateway();
		assertNotNull("Should have created handler", app.currentNetworkInterfaceHandler);
		assertNotNull("Should have created gateway", app.currentNetworkInterfaceGateway);

		app.setServerInfo(server2, key2);
		assertEquals("Didn't update Configinfo name?", server2, app.getConfigInfo().getServerName());
		assertEquals("Didn't update Configinfo key?", key2, app.getConfigInfo().getServerPublicKey());
		assertNull("Should have re-cleared handler", app.currentNetworkInterfaceHandler);
		assertNull("Should have re-cleared gateway", app.currentNetworkInterfaceGateway);
		
		app.loadConfigInfo();
		assertEquals("Didn't save Configinfo name?", server2, app.getConfigInfo().getServerName());
		assertEquals("Didn't save Configinfo key?", key2, app.getConfigInfo().getServerPublicKey());
		
		app.deleteAllFiles();

	}
	
	public void testIsSSLServerAvailable() throws Exception
	{
		assertEquals(false, appWithoutServer.isSSLServerAvailable());
		assertEquals(true, appWithServer.isSSLServerAvailable());
		MockMartusApp appWithoutServerName = MockMartusApp.create();
		assertEquals("Empty server name was available?", false, appWithoutServerName.isSSLServerAvailable(""));
		assertEquals("uninitialized app server available?", false, appWithoutServerName.isSSLServerAvailable());
		assertNull("No proxy?", appWithoutServerName.currentNetworkInterfaceHandler);
		appWithoutServerName.deleteAllFiles();
	}

	public void testGetPublicCodeFromAccount() throws Exception
	{
		MockMartusSecurity security = new MockMartusSecurity();
		String publicKeyString = security.getPublicKeyString();
		String publicCode = MartusUtilities.computePublicCode(publicKeyString);
		assertEquals("wrong code?", "71887634433124687372", publicCode);
	}
		

	public void testGetServerPublicKeyNoServer() throws Exception
	{
		MartusServer noServer = null;
		try
		{
			noServer = new MockServerNotAvailable();
			appWithoutServer.getServerPublicKey(new ServerSideNetworkHandlerForNonSSL(noServer));
			fail("Should have thrown");
		}
		catch(MartusApp.ServerNotAvailableException expectedException)
		{
			MockMartusServer mock = (MockMartusServer) noServer;
			mock.deleteAllFiles();
		}
	}
		
	public void testGetServerPublicKeyInvalidResponse() throws Exception
	{
		Vector response = new Vector();
		response.add(NetworkInterfaceConstants.OK);
		response.add("some invalid stuff");
		response.add("whatever");
		mockServer.infoResponse = response;
		try
		{
			appWithoutServer.getServerPublicKey(mockNonSSLServerHandler);
			fail("Should have thrown");
		}
		catch(MartusApp.PublicInformationInvalidException expectedException)
		{
		}
		mockServer.infoResponse = null;
	}
	
	public void testGetServerPublicKeyBadSig() throws Exception
	{
		mockSecurityForApp.loadSampleAccount();
		Vector response = new Vector();
		response.add(NetworkInterfaceConstants.OK);
		response.add(mockSecurityForApp.getPublicKeyString());
		response.add(Base64.encode(new String("whatever").getBytes()));
		mockServer.infoResponse = response;
		try
		{
			appWithoutServer.getServerPublicKey(mockNonSSLServerHandler);
			fail("Should have thrown");
		}
		catch(MartusApp.PublicInformationInvalidException expectedException)
		{
		}
		mockServer.infoResponse = null;
	}
	
	public void testGetServerPublicKey() throws Exception
	{
		MockMartusSecurity securityWithAccount = new MockMartusSecurity();
		mockServer.setSecurity(securityWithAccount);
		String publicKey = appWithAccount.getServerPublicKey(mockNonSSLServerHandler);
		assertEquals("wrong key?", securityWithAccount.getPublicKeyString(), publicKey);
	}
	

	public void testRequestServerUploadRights() throws Exception
	{
		String clientId = appWithAccount.getAccountId();
		mockSecurityForApp.loadSampleAccount();
		mockServer.setMagicWord(sampleMagicWord);
		
		assertEquals("can upload already?", false, mockServer.canClientUpload(clientId));
		assertEquals("wrong word worked?", false, appWithAccount.requestServerUploadRights("wrong word"));
		assertEquals("empty word worked?", false, appWithAccount.requestServerUploadRights(""));
		assertEquals("can upload?", false, mockServer.canClientUpload(clientId));
		
		mockServer.subtractMaxFailedUploadAttemptsFromCounter();
		
		assertEquals("right word failed?", true, appWithAccount.requestServerUploadRights(sampleMagicWord));
		assertEquals("still can't upload?", true, mockServer.canClientUpload(clientId));
		assertEquals("empty word failed after right word passed?", true, appWithAccount.requestServerUploadRights(""));
		mockServer.setMagicWord(null);
	}
	
	class MockMartusServerChunks extends MockMartusServer 
	{
	
		public MockMartusServerChunks() throws Exception 
		{
			super();
		}

		public String uploadBulletin(String authorAccountId, String bulletinLocalId, String data) 
		{
			fail("Should not be called, using chunks");
			return "";
		}

		public String uploadBulletinChunk(String authorAccountId, String bulletinLocalId, int totalSize, int chunkOffset, int chunkSize, String data, String signature) 
		{
			fail("Should not be called--use putBulletinChunk instead!");
			return "";
		}

		public String putBulletinChunk(String uploaderAccountId, String authorAccountId, String bulletinLocalId,
				int chunkOffset, int chunkSize, int totalSize, String data) 
		{
			++chunkCount;
			return super.putBulletinChunk(uploaderAccountId, authorAccountId, bulletinLocalId,
				chunkOffset, chunkSize, totalSize, data);
		}

		int chunkCount;

	}
	
	public void testGetFileLength() throws Exception
	{
		class MockFile extends File
		{
			MockFile()
			{
				super(".");
			}
			
			public long length()
			{
				return mockLength;
			}
			
			long mockLength;
		}
		
		MockFile mockFile = new MockFile();
		final int normalLength = 555;
		mockFile.mockLength = normalLength;
		assertEquals(normalLength, MartusUtilities.getCappedFileLength(mockFile));
		
		mockFile.mockLength = 10L *1024*1024*1024;
		try
		{
			MartusUtilities.getCappedFileLength(mockFile);
			fail("Should have thrown too large for big number");
		}
		catch(MartusUtilities.FileTooLargeException ignoreExpectedException)
		{
		}


		mockFile.mockLength = -255;
		try
		{
			MartusUtilities.getCappedFileLength(mockFile);
			fail("Should have thrown too large for negative number");
		}
		catch(MartusUtilities.FileTooLargeException ignoreExpectedException)
		{
		}
	}
	
	public void testUploadBulletinUsesChunks() throws Exception
	{
		NetworkInterface oldSSLServer = appWithAccount.currentNetworkInterfaceHandler;
		MockMartusServerChunks server = new MockMartusServerChunks();
		server.initialize();
		server.setSecurity(mockSecurityForServer);
		server.clientsThatCanUpload.clear();
		server.allowUploads(appWithAccount.getAccountId());
		appWithAccount.setSSLNetworkInterfaceHandlerForTesting(new ServerSideNetworkHandler(server));
		appWithAccount.serverChunkSize = 100;
		Bulletin b = appWithAccount.createBulletin();
		b.setSealed();
		b.save();
		assertEquals("result not ok?", NetworkInterfaceConstants.OK, appWithAccount.uploadBulletin(b, null));
		assertTrue("count not > 1?", server.chunkCount > 1);

		server.uploadResponse = NetworkInterfaceConstants.INVALID_DATA;
		assertEquals("result ok?", NetworkInterfaceConstants.INVALID_DATA, appWithAccount.uploadBulletin(b, null));

		appWithAccount.setSSLNetworkInterfaceHandlerForTesting(oldSSLServer);
		appWithAccount.serverChunkSize = NetworkInterfaceConstants.MAX_CHUNK_SIZE;
		
		server.deleteAllFiles();
	}

	public void testBackgroundUploadSealedWithBadPort() throws Exception
	{
		TRACE_BEGIN("testBackgroundUploadSealedWithBadPort");

		createSealedBulletin(appWithoutServer);
		assertNull("No server", appWithoutServer.backgroundUpload(null));
		assertEquals("Bulletin disappeared?", 1, appWithoutServer.getFolderOutbox().getBulletinCount());
		TRACE_END();
	}

	public void testBackgroundUploadDraftWithBadPort() throws Exception
	{
		TRACE_BEGIN("testBackgroundUploadDraftWithBadPort");

		createDraftBulletin(appWithoutServer);
		assertNull("No server", appWithoutServer.backgroundUpload(null));
		assertEquals("Bulletin disappeared?", 1, appWithoutServer.getFolderDraftOutbox().getBulletinCount());
		TRACE_END();
	}

	public void testBackgroundUploadNothingToSend() throws Exception
	{
		TRACE_BEGIN("testBackgroundUploadNothingToSend");
		mockSecurityForApp.loadSampleAccount();
		BulletinFolder outbox = appWithServer.getFolderOutbox();

		assertEquals("Empty outbox", 0, outbox.getBulletinCount());
		assertNull("Empty outbox", appWithServer.backgroundUpload(null));
		TRACE_END();
	}

	public void testBackgroundUploadSealedWorked() throws Exception
	{
		TRACE_BEGIN("testBackgroundUploadSealedWorked");
		mockSecurityForApp.loadSampleAccount();
		assertTrue("must be able to ping", appWithAccount.isSSLServerAvailable());
		BulletinFolder outbox = appWithAccount.getFolderOutbox();
		
		mockServer.allowUploads(appWithAccount.getAccountId());

		createSealedBulletin(appWithAccount);
		assertEquals("Should work", NetworkInterfaceConstants.OK, appWithAccount.backgroundUpload(null));
		assertEquals("It was sent", 0, outbox.getBulletinCount());
		assertEquals("It was sent", 1, appWithAccount.getFolderSent().getBulletinCount());

		assertNull("Again Empty outbox", appWithAccount.backgroundUpload(null));
		mockServer.clientsThatCanUpload.clear();
		TRACE_END();
	}

	public void testBackgroundUploadDraftWorked() throws Exception
	{
		TRACE_BEGIN("testBackgroundUploadDraftWorked");
		mockSecurityForApp.loadSampleAccount();
		assertTrue("must be able to ping", appWithAccount.isSSLServerAvailable());
		BulletinFolder draftOutbox = appWithAccount.getFolderDraftOutbox();
		
		mockServer.allowUploads(appWithAccount.getAccountId());

		createDraftBulletin(appWithAccount);
		createDraftBulletin(appWithAccount);
		assertEquals("first returned an error?", NetworkInterfaceConstants.OK, appWithAccount.backgroundUpload(null));
		assertEquals("first didn't get removed?", 1, draftOutbox.getBulletinCount());
		assertEquals("second returned an error?", NetworkInterfaceConstants.OK, appWithAccount.backgroundUpload(null));
		assertEquals("second didn't get removed?", 0, draftOutbox.getBulletinCount());

		mockServer.clientsThatCanUpload.clear();
		TRACE_END();
	}

	public void testBackgroundUploadSealedFail() throws Exception
	{
		TRACE_BEGIN("testBackgroundUploadSealedFail");
		mockSecurityForApp.loadSampleAccount();
		assertTrue("must be able to ping", appWithServer.isSSLServerAvailable());
		BulletinFolder outbox = appWithServer.getFolderOutbox();

		createSealedBulletin(appWithServer);
		String FAILRESULT = "Some error tag would go here";
		mockServer.uploadResponse = FAILRESULT;
		assertEquals("Should fail", FAILRESULT, appWithServer.backgroundUpload(null));
		assertEquals("Still in outbox", 1, outbox.getBulletinCount());
		assertEquals("Not in sent folder", 0, appWithServer.getFolderSent().getBulletinCount());
		Bulletin stillSealed = outbox.getBulletinSorted(0);
		assertTrue("Should still be sealed", stillSealed.isSealed());
		mockServer.uploadResponse = null;
		TRACE_END();
	}

	public void testBackgroundUploadDraftFail() throws Exception
	{
		TRACE_BEGIN("testBackgroundUploadDraftFail");
		mockSecurityForApp.loadSampleAccount();
		assertTrue("must be able to ping", appWithServer.isSSLServerAvailable());
		BulletinFolder draftOutbox = appWithServer.getFolderDraftOutbox();

		createDraftBulletin(appWithServer);
		String FAILRESULT = "Some error tag would go here";
		mockServer.uploadResponse = FAILRESULT;
		assertEquals("Should fail", FAILRESULT, appWithServer.backgroundUpload(null));
		assertEquals("Still in draft outbox", 1, draftOutbox.getBulletinCount());
		mockServer.uploadResponse = null;
		TRACE_END();
	}

	public void testBackgroundUploadLogging() throws Exception
	{
		TRACE_BEGIN("testBackgroundUploadLogging");
		String serverName = "some silly server";
		appWithServer.setServerInfo(serverName, mockServer.getAccountId());
		appWithServer.setSSLNetworkInterfaceHandlerForTesting(mockSSLServerHandler);
		File logFile = new File(appWithServer.getUploadLogFilename());
		logFile.delete();

		createSealedBulletin(appWithServer);
		mockServer.uploadResponse = NetworkInterfaceConstants.OK;
		assertEquals("Should work", NetworkInterfaceConstants.OK, appWithServer.backgroundUpload(null));
		assertEquals("Created a log?", false, logFile.exists());

		appWithServer.enableUploadLogging();
		Bulletin logged = createSealedBulletin(appWithServer);
		assertEquals("Should work", NetworkInterfaceConstants.OK, appWithServer.backgroundUpload(null));
		assertEquals("No log?", true, logFile.exists());
		mockServer.uploadResponse = null;

		UnicodeReader reader = new UnicodeReader(logFile);
		String line1 = reader.readLine();
		assertEquals(logged.getLocalId(), line1);
		String line2 = reader.readLine();
		assertEquals(serverName, line2);
		String line3 = reader.readLine();
		assertEquals(logged.get(Bulletin.TAGTITLE), line3);
		reader.close();
		
		TRACE_END();
	}

	public void testRetrieveBulletinsOnlyBadId() throws Exception
	{
		TRACE_BEGIN("testRetreiveBulletinsBadId");
		mockSecurityForApp.loadSampleAccount();
		assertTrue("must be able to ping", appWithServer.isSSLServerAvailable());

		mockServer.setDownloadResponseNotFound();
		Vector badList = new Vector();
		badList.add("not an id");
		Retriever retriever = new Retriever(appWithServer, null);	
		retriever.retrieveBulletins(badList, appWithServer.createFolderRetrieved());
		assertEquals(NetworkInterfaceConstants.INCOMPLETE, retriever.getResult());
		mockServer.setDownloadResponseReal();
		TRACE_END();
	}

	public void testRetrieveBulletinsWithOneBadId() throws Exception
	{
		TRACE_BEGIN("testRetreiveBulletinsBadId");
		mockSecurityForApp.loadSampleAccount();
		assertTrue("must be able to ping", appWithServer.isSSLServerAvailable());

		Bulletin b1 = appWithAccount.createBulletin();
		b1.setSealed();
		b1.save();
		Bulletin b2 = appWithAccount.createBulletin();
		b2.setSealed();
		b2.save();
		Bulletin b3 = appWithAccount.createBulletin();
		b3.set(Bulletin.TAGAUTHOR, "author");
		b3.setSealed();
		b3.save();
		mockServer.allowUploads(appWithAccount.getAccountId());
		assertEquals("upload b1", NetworkInterfaceConstants.OK, appWithAccount.uploadBulletin(b1, null));
		assertEquals("upload b2", NetworkInterfaceConstants.OK, appWithAccount.uploadBulletin(b2, null));
		assertEquals("upload b3", NetworkInterfaceConstants.OK, appWithAccount.uploadBulletin(b3, null));

		BulletinStore store = appWithAccount.getStore();
		store.removeBulletinFromStore(b1.getUniversalId());
		store.removeBulletinFromStore(b2.getUniversalId());
		store.removeBulletinFromStore(b3.getUniversalId());
		
		Vector withBadId = new Vector();
		withBadId.add(b1.getUniversalId());
		withBadId.add(b2.getUniversalId());
		withBadId.add(UniversalId.createDummyUniversalId());
		withBadId.add(b3.getUniversalId());

		Retriever retriever = new Retriever(appWithAccount, null);	
		retriever.retrieveBulletins(withBadId, appWithAccount.createFolderRetrieved());
		assertEquals("retrieve all", NetworkInterfaceConstants.INCOMPLETE, retriever.getResult());
		assertEquals("not back to three?", 3, store.getBulletinCount());

		TRACE_END();
	}

	public void testRetrieveBulletinsEmptyList() throws Exception
	{
		TRACE_BEGIN("testRetrieveBulletinsEmptyList");

		assertTrue("must be able to ping", appWithServer.isSSLServerAvailable());
		
		appWithAccount.getStore().deleteAllData();
		BulletinStore store = appWithAccount.getStore();
		assertEquals("No bulletins", 0, store.getBulletinCount());

		Vector empty = new Vector();
		Retriever retriever = new Retriever(appWithAccount, null);	
		retriever.retrieveBulletins(empty, appWithAccount.createFolderRetrieved());
		assertEquals("empty", NetworkInterfaceConstants.OK, retriever.getResult());
		assertEquals("Empty didn't even ask", null, mockServer.lastClientId);
		assertEquals("Empty didn't download", 0, store.getBulletinCount());

		TRACE_END();
	}

		
	public void testRetrieveBulletins() throws Exception
	{
		TRACE_BEGIN("testRetrieveBulletins");

		assertTrue("must be able to ping", appWithServer.isSSLServerAvailable());
	
		BulletinStore store = appWithAccount.getStore();

		Bulletin b1 = appWithAccount.createBulletin();
		b1.setSealed();
		b1.save();
		Vector justB1 = new Vector();
		justB1.add(b1.getUniversalId());
		Retriever retriever = new Retriever(appWithAccount, null);	
		retriever.retrieveBulletins(justB1, appWithAccount.createFolderRetrieved());
		assertEquals("justB1", NetworkInterfaceConstants.OK, retriever.getResult());
		assertEquals("justB1 didn't even ask", null, mockServer.lastClientId);
		assertEquals("justB1 didn't download", 1, store.getBulletinCount());

		Vector nonExistantUidList = new Vector();
		UniversalId uId1 = UniversalId.createDummyUniversalId();
		nonExistantUidList.add(uId1);
		
		Vector errorResponse = new Vector();
		String errorString = "some unknown error";
		errorResponse.add(errorString);

		mockServer.downloadResponse = errorResponse;
		retriever.retrieveBulletins(nonExistantUidList, appWithAccount.createFolderRetrieved());
		assertEquals("unknownId", NetworkInterfaceConstants.INCOMPLETE, retriever.getResult());
		mockServer.downloadResponse = null;

		Bulletin b2 = appWithAccount.createBulletin();
		b2.setSealed();
		b2.save();
		Bulletin b3 = appWithAccount.createBulletin();
		b3.set(Bulletin.TAGAUTHOR, "author");
		b3.setSealed();
		b3.save();
		mockServer.allowUploads(appWithAccount.getAccountId());
		assertEquals("upload b1", NetworkInterfaceConstants.OK, appWithAccount.uploadBulletin(b1, null));
		assertEquals("upload b2", NetworkInterfaceConstants.OK, appWithAccount.uploadBulletin(b2, null));
		assertEquals("upload b3", NetworkInterfaceConstants.OK, appWithAccount.uploadBulletin(b3, null));
		store.removeBulletinFromStore(b1.getUniversalId());
		store.removeBulletinFromStore(b3.getUniversalId());
		assertEquals("not just one left?", 1, store.getBulletinCount());
		
		Vector allThree = new Vector();
		allThree.add(b1.getUniversalId());
		allThree.add(b2.getUniversalId());
		allThree.add(b3.getUniversalId());
		retriever.retrieveBulletins(allThree, appWithAccount.createFolderRetrieved());
		assertEquals("retrieve all", NetworkInterfaceConstants.OK, retriever.getResult());
		assertEquals("not back to three?", 3, store.getBulletinCount());
		
		Bulletin b3got = store.findBulletinByUniversalId(b3.getUniversalId());
		assertEquals("missing author?", b3.get(Bulletin.TAGAUTHOR), b3got.get(Bulletin.TAGAUTHOR));
		
		TRACE_END();
	}

	public void testRetrieveBulletinManyChunks() throws Exception
	{
		TRACE_BEGIN("testRetrieveBulletinThreeChunks");

		Bulletin b = createAndUploadSampleBulletin();
		
		appWithAccount.serverChunkSize = 100;
		BulletinStore store = appWithAccount.getStore();
		appWithAccount.retrieveOneBulletinToFolder(b.getUniversalId(), store.getFolderDiscarded(), null);
		
		appWithAccount.serverChunkSize = NetworkInterfaceConstants.MAX_CHUNK_SIZE;
		
		TRACE_END();
	}

	public void testRetrieveBulletinResponseSimple() throws Exception
	{
		TRACE_BEGIN("testRetrieveBulletinResponseSimple");

		Bulletin b = createAndUploadSampleBulletin();
		byte[] bulletinBytes = getBulletinZipBytes(b);
		int totalSize = bulletinBytes.length;
		int chunkSize = totalSize;

		Vector response = new Vector();
		response.add(NetworkInterfaceConstants.OK);
		response.add(new Integer(totalSize));
		response.add(new Integer(chunkSize));
		response.add(Base64.encode(bulletinBytes));
		mockServer.downloadResponse = response;
		
		appWithAccount.retrieveOneBulletinToFolder(b.getUniversalId(), appWithAccount.getFolderDiscarded(), null);
		
		mockServer.setDownloadResponseReal();
		
		TRACE_END();
	}
	
	public void testRetrieveBulletinResponseChunkSizeInvalid() throws Exception
	{
		TRACE_BEGIN("testRetrieveBulletinResponseChunkSizeInvalid");

		Bulletin b = createAndUploadSampleBulletin();
		byte[] bulletinBytes = getBulletinZipBytes(b);
		int totalSize = bulletinBytes.length;
		int chunkSize = -1;

		Vector response = new Vector();
		response.add(NetworkInterfaceConstants.OK);
		response.add(new Integer(totalSize));
		response.add(new Integer(chunkSize));
		response.add(MockBulletin.saveToZipString(b));
		mockServer.downloadResponse = response;
		
		try
		{
			appWithAccount.retrieveOneBulletinToFolder(b.getUniversalId(), appWithAccount.getFolderDiscarded(), null);
			fail("Should have thrown");
		}
		catch(Exception ignoreExpectedException)
		{
		}
		
		mockServer.setDownloadResponseReal();
		
		TRACE_END();
	}
	
	public void testRetrieveBulletinResponseTotalSizeInvalid() throws Exception
	{
		TRACE_BEGIN("testRetrieveBulletinResponseTotalSizeInvalid");

		Bulletin b = createAndUploadSampleBulletin();
		byte[] bulletinBytes = getBulletinZipBytes(b);

		Vector response = new Vector();
		response.add(NetworkInterfaceConstants.OK);
		response.add(new Integer(-1));
		response.add(new Integer(bulletinBytes.length));
		response.add(MockBulletin.saveToZipString(b));
		mockServer.downloadResponse = response;
		
		try
		{
			appWithAccount.retrieveOneBulletinToFolder(b.getUniversalId(), appWithAccount.getFolderDiscarded(), null);
			fail("Should have thrown");
		}
		catch(Exception ignoreExpectedException)
		{
		}
		
		mockServer.setDownloadResponseReal();
		
		TRACE_END();
	}
	
	public void testRetrieveBulletinResponseChunkSizeLargerThanRemainingSize() throws Exception
	{
		TRACE_BEGIN("testRetrieveBulletinResponseChunkSizeLargerThanRemainingSize");

		Bulletin b = createAndUploadSampleBulletin();
		byte[] bulletinBytes = getBulletinZipBytes(b);

		Vector response = new Vector();
		response.add(NetworkInterfaceConstants.CHUNK_OK);
		response.add(new Integer(bulletinBytes.length));
		response.add(new Integer(bulletinBytes.length / 3 * 2));
		response.add(MockBulletin.saveToZipString(b));
		mockServer.downloadResponse = response;
		
		try
		{
			appWithAccount.retrieveOneBulletinToFolder(b.getUniversalId(), appWithAccount.getFolderDiscarded(), null);
			fail("Should have thrown");
		}
		catch(Exception ignoreExpectedException)
		{
		}
		
		mockServer.setDownloadResponseReal();
		
		TRACE_END();
	}
	
	public void testRetrieveBulletinResponseIncorrectChunkSize() throws Exception
	{
		TRACE_BEGIN("testRetrieveBulletinResponseIncorrectChunkSize");

		Bulletin b = createAndUploadSampleBulletin();
		byte[] sampleBytes = "Testing".getBytes();

		Vector response = new Vector();
		response.add(NetworkInterfaceConstants.CHUNK_OK);
		response.add(new Integer(sampleBytes.length));
		response.add(new Integer(sampleBytes.length-1));
		response.add(Base64.encode(sampleBytes));
		mockServer.downloadResponse = response;
		
		try
		{
			appWithAccount.retrieveOneBulletinToFolder(b.getUniversalId(), appWithAccount.getFolderDiscarded(), null);
			fail("Should have thrown");
		}
		catch(MartusUtilities.ServerErrorException ignoreExpectedException)
		{
		}
		
		mockServer.setDownloadResponseReal();
		
		TRACE_END();
	}
	
	public void testRetrieveBulletinResponseIncorrectTotalSize() throws Exception
	{
		TRACE_BEGIN("testRetrieveBulletinResponseIncorrectTotalSize");

		Bulletin b = createAndUploadSampleBulletin();
		byte[] sampleBytes = "Testing".getBytes();

		Vector response = new Vector();
		response.add(NetworkInterfaceConstants.OK);
		response.add(new Integer(sampleBytes.length+1));
		response.add(new Integer(sampleBytes.length));
		response.add(Base64.encode(sampleBytes));
		mockServer.downloadResponse = response;
		
		try
		{
			appWithAccount.retrieveOneBulletinToFolder(b.getUniversalId(), appWithAccount.getFolderDiscarded(), null);
			fail("Should have thrown");
		}
		catch(MartusUtilities.ServerErrorException ignoreExpectedException)
		{
		}
		
		mockServer.setDownloadResponseReal();
		
		TRACE_END();
	}
	
	public void testRetrieveBulletinsNoServer() throws Exception
	{
		TRACE_BEGIN("testRetrieveBulletinsNoServer");
		mockSecurityForApp.loadSampleAccount();

		Vector uploadedIdList = new Vector();
		uploadedIdList.add("sample id");
		Retriever retriever = new Retriever(appWithoutServer, null);	
		retriever.retrieveBulletins(uploadedIdList, appWithoutServer.createFolderRetrieved());
		assertEquals(NetworkInterfaceConstants.NO_SERVER, retriever.getResult());

		TRACE_END();
	}
	
	class MockGateway extends ClientSideNetworkGateway
	{
		MockGateway()
		{
			super(null);
		}
		
		public NetworkResponse deleteServerDraftBulletins(MartusCrypto signer, 
						String authorAccountId, String[] bulletinLocalIds) throws 
				MartusCrypto.MartusSignatureException
		{
			if(throwSigError)
				throw new MartusCrypto.MartusSignatureException();
				
			gotSigner = signer;
			gotAuthor = authorAccountId;
			gotIds = bulletinLocalIds;
			return new NetworkResponse(response);
		}
		
		public NetworkResponse	putContactInfo(MartusCrypto signer, String authorAccountId, Vector contactInfo) throws 
			MartusCrypto.MartusSignatureException
		{
			if(throwSigError)
				throw new MartusCrypto.MartusSignatureException();
			gotSigner = signer;
			gotAuthor = authorAccountId;
			gotContactInfo = contactInfo;
			return new NetworkResponse(response);
		}

		
		MartusCrypto gotSigner;
		String gotAuthor;
		String[] gotIds;
		Vector gotContactInfo;
		Vector response;
		boolean throwSigError;
	}

	public void testDeleteServerDraftBulletins() throws Exception
	{
		appWithServer.setServerInfo("mock", mockServer.getAccountId());
		MockGateway gateway = new MockGateway();

		MartusSecurity security = new MartusSecurity();
		security.createKeyPair(512);
		MockMartusApp app= MockMartusApp.create(security);
		app.currentNetworkInterfaceGateway = gateway;
		String accountId = app.getAccountId();
		
		Vector uids = new Vector();
		uids.add(BulletinHeaderPacket.createUniversalId(accountId));

		Vector mockResponse = new Vector();
		mockResponse.clear();
		mockResponse.add(NetworkInterfaceConstants.OK);
		gateway.response = mockResponse;
		uids.add(BulletinHeaderPacket.createUniversalId(accountId));
		uids.add(BulletinHeaderPacket.createUniversalId(accountId));
		String result = app.deleteServerDraftBulletins(uids);
		assertEquals("wrong result?", mockResponse.get(0), result);
		assertEquals("wrong crypto?", app.getSecurity(), gateway.gotSigner);
		assertEquals("wrong author?", app.getAccountId(), gateway.gotAuthor);
		assertEquals("wrong id count?", uids.size(), gateway.gotIds.length);
		for (int i = 0; i < gateway.gotIds.length; i++)
		{
			assertEquals("missing id " + i, ((UniversalId)uids.get(i)).getLocalId(), gateway.gotIds[i]);
		}
		
		gateway.throwSigError = true;
		try
		{
			app.deleteServerDraftBulletins(uids);
			fail("Should have thrown for sig error (no key pair)");
		}
		catch (MartusSignatureException ignoreExpectedException)
		{
		}
		gateway.throwSigError = false;

		uids.add(BulletinHeaderPacket.createUniversalId(mockServer.getAccountId()));
		try
		{
			app.deleteServerDraftBulletins(uids);
			fail("Should have thrown for wrong account");
		}
		catch (WrongAccountException ignoreExpectedException)
		{
		}

	}

	public void testPutContactInfo() throws Exception
	{
		appWithServer.setServerInfo("mock", mockServer.getAccountId());
		MockGateway gateway = new MockGateway();

		MartusSecurity security = new MartusSecurity();
		security.createKeyPair(512);
		MockMartusApp app= MockMartusApp.create(security);
		app.currentNetworkInterfaceGateway = gateway;
		
		Vector contact = new Vector();
		contact.add("PublicKey");
		contact.add(new Integer(2));
		contact.add("Author");
		contact.add("Address");
		contact.add("Signature");

		Vector mockResponse = new Vector();
		mockResponse.clear();
		mockResponse.add(NetworkInterfaceConstants.OK);
		gateway.response = mockResponse;

		String result = app.putContactInfoOnServer(contact);
		assertEquals("wrong result?", mockResponse.get(0), result);
		assertEquals("wrong crypto?", app.getSecurity(), gateway.gotSigner);
		assertEquals("wrong author?", app.getAccountId(), gateway.gotAuthor);
		assertEquals("wrong vector count?", contact.size(), gateway.gotContactInfo.size());
		for (int i = 0; i < gateway.gotContactInfo.size(); i++)
		{
			assertEquals("missing contents " + i, contact.get(i), gateway.gotContactInfo.get(i));
		}
		
		gateway.throwSigError = true;
		try
		{
			app.putContactInfoOnServer(contact);
			fail("Should have thrown for sig error (no key pair)");
		}
		catch (MartusSignatureException ignoreExpectedException)
		{
		}
		gateway.throwSigError = false;
	}


	public void testGetFieldOfficeAccountsNoServer() throws Exception
	{
		TRACE_BEGIN("testGetFieldOfficeAccountsNoServer");
		try
		{
			appWithoutServer.getFieldOfficeAccounts();
			fail("Got valid accounts?");
		}
		catch(MartusUtilities.ServerErrorException ignoreExpectedException)
		{
		}
		
		TRACE_END();
	}

	

	public void testGetFieldOfficeAccountsErrors() throws Exception
	{
		TRACE_BEGIN("testGetFieldOfficeAccountsErrors");
		assertTrue("must be able to ping", appWithServer.isSSLServerAvailable());


		mockSSLServerHandler.nullGetFieldOfficeAccountIds(true);
		try
		{
			appWithServer.getFieldOfficeAccounts();
			fail("null response didn't throw?");
		}
		catch(MartusUtilities.ServerErrorException ignoreExpectedException)
		{
		}
		mockSSLServerHandler.nullGetFieldOfficeAccountIds(false);
				
		Vector desiredResult = new Vector();

		desiredResult.add(NetworkInterfaceConstants.REJECTED);
		mockServer.listFieldOfficeAccountsResponse = desiredResult;
		try
		{
			appWithServer.getFieldOfficeAccounts();
			fail("rejected didn't throw?");
		}
		catch(MartusUtilities.ServerErrorException ignoreExpectedException)
		{
		}
		mockServer.listFieldOfficeAccountsResponse = null;

		TRACE_END();
	}

	public void testRetrievePublicDataPacket() throws Exception
	{
		TRACE_BEGIN("testRetrievePublicDataPacket");
		String sampleSummary1 = "this is a basic summary";
		String sampleSummary2 = "another silly summary";
		
		Bulletin b1 = appWithAccount.createBulletin();
		b1.setAllPrivate(true);
		b1.set(Bulletin.TAGTITLE, sampleSummary1);
		b1.setSealed();
		b1.save();
		
		Bulletin b2 = appWithAccount.createBulletin();
		b2.setAllPrivate(false);
		b2.set(Bulletin.TAGTITLE, sampleSummary2);
		b2.setSealed();
		b2.save();

		String accountId = appWithAccount.getAccountId();
		mockServer.allowUploads(accountId);
		assertEquals("failed upload1?", NetworkInterfaceConstants.OK, appWithAccount.uploadBulletin(b1, null));
		assertEquals("failed upload2?", NetworkInterfaceConstants.OK, appWithAccount.uploadBulletin(b2, null));

		String fdpId1 = b1.getFieldDataPacket().getLocalId();		
		String fdpId2 = b2.getFieldDataPacket().getLocalId();		

		FieldDataPacket fdp1 = appWithAccount.retrieveFieldDataPacketFromServer(accountId, b1.getLocalId(), fdpId1);
		FieldDataPacket fdp2 = appWithAccount.retrieveFieldDataPacketFromServer(accountId, b2.getLocalId(), fdpId2);

		String title1 = fdp1.get(Bulletin.TAGTITLE);
		String title2 = fdp2.get(Bulletin.TAGTITLE);
		
		assertEquals("Bad title1", sampleSummary1, title1);
		assertEquals("Bad title2", sampleSummary2, title2);
		TRACE_END();
	}
	
	public void testRetrievePublicDataPacketErrors() throws Exception
	{
		TRACE_BEGIN("testRetrievePublicDataPacketErrors");
		try
		{
			appWithAccount.retrieveFieldDataPacketFromServer("account", "123", "xyz");
			fail("Didn't throw Error for bad ID?");
		}
		catch(MartusUtilities.ServerErrorException ignoreExpectedException)
		{
		}
		
		try
		{
			Bulletin b1 = appWithAccount.createBulletin();
			String fdpId1 = b1.getFieldDataPacket().getLocalId();		
			appWithAccount.retrieveFieldDataPacketFromServer(appWithAccount.getAccountId(), b1.getLocalId(), fdpId1);
			fail("Didn't throw Error for bad Missing Packet on Server");
		}
		catch(MartusUtilities.ServerErrorException ignoreExpectedException)
		{
		}

		TRACE_END();
	}

	
	public void testGetFieldOfficeAccounts() throws Exception
	{
		TRACE_BEGIN("testGetFieldOfficeAccounts");
		Vector desiredResult = new Vector();

		desiredResult.add(NetworkInterfaceConstants.OK);
		desiredResult.add("Account1");
		desiredResult.add("Account2");
		
		mockServer.listFieldOfficeAccountsResponse = desiredResult;
		Vector result = appWithServer.getFieldOfficeAccounts();
		mockServer.listFieldOfficeAccountsResponse = null;
		assertNotNull("Got back null?", result);
		assertEquals("Wrong size?", 2, result.size());
		assertEquals("Wrong account?", "Account1", result.get(0));
		assertEquals("Wrong 2nd account?", "Account2", result.get(1));
		TRACE_END();
	}
	
	public void testDownloadFieldOfficeBulletins() throws Exception
	{
		TRACE_BEGIN("testDownloadFieldOfficeBulletins");

		MockMartusSecurity hqSecurity = new MockMartusSecurity();	
		hqSecurity.createKeyPair();
		MockMartusApp hqApp = MockMartusApp.create(hqSecurity);
		hqApp.setServerInfo("mock", mockServer.getAccountId());
		hqApp.setSSLNetworkInterfaceHandlerForTesting(mockSSLServerHandler);
		assertNotEquals("same public key?", appWithAccount.getAccountId(), hqApp.getAccountId());
		appWithAccount.setHQKey(hqApp.getAccountId());

		String sampleSummary1 = "this is a basic summary";
		String sampleSummary2 = "another silly summary";
		String sampleSummary3 = "not my HQ";
		
		Bulletin b1 = appWithAccount.createBulletin();
		b1.setAllPrivate(true);
		b1.set(Bulletin.TAGTITLE, sampleSummary1);
		b1.setSealed();
		appWithAccount.setHQKeyInBulletin(b1);
		b1.save();
		
		Bulletin b2 = appWithAccount.createBulletin();
		b2.setAllPrivate(false);
		b2.set(Bulletin.TAGTITLE, sampleSummary2);
		b2.setSealed();
		appWithAccount.setHQKeyInBulletin(b2);
		b2.save();
		
		Bulletin b3 = appWithAccount.createBulletin();
		b3.set(Bulletin.TAGTITLE, sampleSummary3);
		b3.setSealed();
		b3.save();

		mockServer.allowUploads(appWithAccount.getAccountId());
		assertEquals("failed upload1?", NetworkInterfaceConstants.OK, appWithAccount.uploadBulletin(b1, null));
		assertEquals("failed upload2?", NetworkInterfaceConstants.OK, appWithAccount.uploadBulletin(b2, null));

		Vector uidList = new Vector();
		uidList.add(b1.getUniversalId());
		uidList.add(b2.getUniversalId());
		Retriever retriever = new Retriever(hqApp, null);	
		retriever.retrieveBulletins(uidList, hqApp.createFolderRetrievedFieldOffice());
		assertEquals("retrieve field office bulletins failed?", NetworkInterfaceConstants.OK, retriever.getResult());

		uidList.clear();
		uidList.add(b3.getUniversalId());
		retriever.retrieveBulletins(uidList, hqApp.createFolderRetrievedFieldOffice());
		assertEquals("retrieve non-field office bulletins worked?", NetworkInterfaceConstants.INCOMPLETE, retriever.getResult());

		hqApp.deleteAllFiles();
		TRACE_END();
	}


	public void testEncryptPublicData() throws Exception
	{
		TRACE_BEGIN("testEncryptPublicData");
		MockMartusApp app = MockMartusApp.create();
		assertEquals("App Not Encypting Public?", true, app.getStore().mustEncryptPublicData());
		app.deleteAllFiles();
		
		TRACE_END();
	}

	public void testCreateAccount() throws Exception
	{
		TRACE_BEGIN("testCreateAccount");

		mockSecurityForApp.clearKeyPair();
		try
		{

			File badFile = new File(BAD_FILENAME);
			appWithServer.createAccountInternal(badFile, userName, userPassword);
			fail("Can't create an account if we can't write the file!");

		}
		catch(MartusApp.CannotCreateAccountFileException e)
		{
			// expected exception
		}
		assertEquals("store account not unset on error?", false, mockSecurityForApp.hasKeyPair());


		File keyPairFile = appWithServer.getKeyPairFile();
		File backupKeyPairFile = MartusApp.getBackupFile(keyPairFile);
		keyPairFile.delete();
		backupKeyPairFile.delete();
		appWithServer.createAccount(userName, userPassword);
		assertEquals("no key file?", true, keyPairFile.exists());
		assertEquals("no backup key file?", true, backupKeyPairFile.exists());
		assertEquals("store account not set?", mockSecurityForApp.getPublicKeyString(), appWithServer.getStore().getAccountId());
		assertEquals("User name not set?",userName, appWithServer.getUserName());
		verifySignInThatWorks(appWithServer);

		try
		{
			appWithServer.createAccount(userName, userPassword);
			fail("Can't create an account if one already exists!");
		}
		catch(MartusApp.AccountAlreadyExistsException e)
		{
			// expected exception
		}
		assertEquals("store account not kept if already exists?", mockSecurityForApp.getPublicKeyString(), appWithServer.getStore().getAccountId());
		
		TRACE_END();
	}
	
	public void testExportPublicInfo() throws Exception
	{
		File temp = createTempFile();
		temp.delete();
		appWithAccount.exportPublicInfo(temp);
		assertTrue("not created?", temp.exists());
		UnicodeReader reader = new UnicodeReader(temp);
		String publicKey = reader.readLine();
		String signature = reader.readLine();
		reader.close();
		assertEquals("Public Key wrong?", appWithAccount.getSecurity().getPublicKeyString(), publicKey);
		appWithServer.validatePublicInfo(publicKey, signature);
	}
	
	public void testExtractPublicInfo() throws Exception
	{
		File temp = createTempFile();
		temp.delete();
		appWithAccount.exportPublicInfo(temp);
		String publicKey = appWithAccount.extractPublicInfo(temp);
		assertEquals("Public Key wrong?", appWithAccount.getSecurity().getPublicKeyString(), publicKey);
		
		UnicodeWriter writer = new UnicodeWriter(temp);
		writer.write("flkdjfl");
		writer.close();
		try
		{
			appWithAccount.extractPublicInfo(temp);
			fail("Should have thrown exception");
		}
		catch (Exception ignoreExpectedException)
		{
		}
	}

	void verifySignInThatWorks(MartusApp appWithRealAccount) throws Exception
	{
		assertEquals("should work", true, appWithRealAccount.attemptSignIn(userName, userPassword));
		assertEquals("store account not set?", mockSecurityForApp.getPublicKeyString(), appWithAccount.getStore().getAccountId());
		assertEquals("wrong username?", userName, appWithRealAccount.getUserName());
	}
	
	public void testCenter()
	{
		TRACE_BEGIN("testCenter");
		{
			Point upperLeft = MartusApp.center(new Dimension(800, 600), new Rectangle(0, 0, 800, 600));
			assertEquals(0, upperLeft.x);
			assertEquals(0, upperLeft.y);
		}
		{
			Point upperLeft = MartusApp.center(new Dimension(400, 300), new Rectangle(0, 0, 800, 600));
			assertEquals(200, upperLeft.x);
			assertEquals(150, upperLeft.y);
		}
		TRACE_END();
	}

	public void testFieldLabels()
	{
		TRACE_BEGIN("testFieldLabels");
		assertEquals("Keep ALL Information Private", appWithServer.getFieldLabel("allprivate"));
		assertEquals("Author", appWithServer.getFieldLabel("author"));
		assertEquals("Organization", appWithServer.getFieldLabel("organization"));
		assertEquals("Title", appWithServer.getFieldLabel("title"));
		assertEquals("Location", appWithServer.getFieldLabel("location"));
		assertEquals("Date of Event", appWithServer.getFieldLabel("eventdate"));
		assertEquals("Date Entered", appWithServer.getFieldLabel("entrydate"));
		assertEquals("Keywords", appWithServer.getFieldLabel("keywords"));
		assertEquals("Summary", appWithServer.getFieldLabel("summary"));
		assertEquals("Details", appWithServer.getFieldLabel("publicinfo"));
		assertEquals("Private", appWithServer.getFieldLabel("privateinfo"));
		assertEquals("Language", appWithServer.getFieldLabel("language"));
		TRACE_END();
	}
	
	public void testFolderLabels()
	{
		//assertEquals("Retrieved Bulletins", appWithServer.getFolderLabel("%RetrievedMyBulletin"));
		//assertEquals("Field Desk Bulletins", appWithServer.getFolderLabel("%RetrievedFieldOfficeBulletin"));
	}

	public void testLanguageNames()
	{
		TRACE_BEGIN("testLanguageNames");
		assertNotNull(appWithServer.getLanguageName("Not a valid code"));
		assertEquals("English", appWithServer.getLanguageName("en"));
		assertEquals("Arabic", appWithServer.getLanguageName("ar"));
		assertEquals("Azerbaijani", appWithServer.getLanguageName("az"));
		assertEquals("Bengali", appWithServer.getLanguageName("bn"));
		assertEquals("Burmese", appWithServer.getLanguageName("my"));
		assertEquals("Chinese", appWithServer.getLanguageName("zh"));
		assertEquals("Dutch", appWithServer.getLanguageName("nl"));
		assertEquals("Esperanto", appWithServer.getLanguageName("eo"));
		assertEquals("French", appWithServer.getLanguageName("fr"));
		assertEquals("German", appWithServer.getLanguageName("de"));
		assertEquals("Gujarati", appWithServer.getLanguageName("gu"));
		assertEquals("Hausa", appWithServer.getLanguageName("ha"));
		assertEquals("Hebrew", appWithServer.getLanguageName("he"));
		assertEquals("Hindi", appWithServer.getLanguageName("hi"));
		assertEquals("Hungarian", appWithServer.getLanguageName("hu"));
		assertEquals("Italian", appWithServer.getLanguageName("it"));
		assertEquals("Japanese", appWithServer.getLanguageName("ja"));
		assertEquals("Javanese", appWithServer.getLanguageName("jv"));
		assertEquals("Kannada", appWithServer.getLanguageName("kn"));
		assertEquals("Korean", appWithServer.getLanguageName("ko"));
		assertEquals("Malayalam", appWithServer.getLanguageName("ml"));
		assertEquals("Marathi", appWithServer.getLanguageName("mr"));
		assertEquals("Oriya", appWithServer.getLanguageName("or"));
		assertEquals("Panjabi", appWithServer.getLanguageName("pa"));
		assertEquals("Polish", appWithServer.getLanguageName("pl"));
		assertEquals("Portuguese", appWithServer.getLanguageName("pt"));
		assertEquals("Romanian", appWithServer.getLanguageName("ro"));
		assertEquals("Russian", appWithServer.getLanguageName("ru"));
		assertEquals("Serbian", appWithServer.getLanguageName("sr"));
		assertEquals("Sindhi", appWithServer.getLanguageName("sd"));
		assertEquals("Sinhalese", appWithServer.getLanguageName("si"));
		assertEquals("Spanish", appWithServer.getLanguageName("es"));
		assertEquals("Tamil", appWithServer.getLanguageName("ta"));
		assertEquals("Telugu", appWithServer.getLanguageName("te"));
		assertEquals("Thai", appWithServer.getLanguageName("th"));
		assertEquals("Turkish", appWithServer.getLanguageName("tr"));
		assertEquals("Ukranian", appWithServer.getLanguageName("uk"));
		assertEquals("Urdu", appWithServer.getLanguageName("ur"));
		assertEquals("Vietnamese", appWithServer.getLanguageName("vi"));
		TRACE_END();
	}

	public void testGetLanguageNameChoices()
	{
		TRACE_BEGIN("testWindowTitles");
		String[] testLanguageCodes = {"es", "en", "si"};
		ChoiceItem[] languageChoicesTest = appWithServer.getLanguageNameChoices(testLanguageCodes);
		assertEquals(languageChoicesTest[0].toString(), appWithServer.getLanguageName("en"));
		assertEquals(languageChoicesTest[1].toString(), appWithServer.getLanguageName("si"));
		assertEquals(languageChoicesTest[2].toString(), appWithServer.getLanguageName("es"));
		TRACE_END();
	}
	
	public void testWindowTitles()
	{
		TRACE_BEGIN("testWindowTitles");
		assertEquals("Martus Human Rights Bulletin System", appWithServer.getWindowTitle("main"));
		TRACE_END();
	}

	public void testButtonLabels()
	{
		TRACE_BEGIN("testButtonLabels");
		assertEquals("Help", appWithServer.getButtonLabel("help"));
		TRACE_END();
	}
	
	public void testMenuLabels()
	{
		TRACE_BEGIN("testMenuLabels");
		assertEquals("File", appWithServer.getMenuLabel("file"));
		TRACE_END();
	}

	public void testCurrentLanguage()
	{
		TRACE_BEGIN("testCurrentLanguage");
		assertEquals("en", appWithServer.getCurrentLanguage());
		assertEquals("MartusHelp-en.txt", appWithServer.getHelpFilename());
		assertEquals("MartusHelp-en.txt", appWithServer.getEnglishHelpFilename());
		appWithServer.setCurrentLanguage("es");
		assertEquals("MartusHelp-es.txt", appWithServer.getHelpFilename());
		assertEquals("es", appWithServer.getCurrentLanguage());
		char iWithAccentInUtf8 = 237;
		char[] titleInSpanish = {'T', iWithAccentInUtf8, 't', 'u', 'l', 'o'};
		assertEquals(new String(titleInSpanish), appWithServer.getFieldLabel("title"));
		appWithServer.setCurrentLanguage("en");
		TRACE_END();
	}

	public void testDateConvert()
	{
		TRACE_BEGIN("testDateConvert");
		assertEquals("12/13/1987", appWithServer.convertStoredToDisplay("1987-12-13"));
		assertEquals("", appWithServer.convertStoredToDisplay("abc"));
		assertEquals("", appWithServer.convertStoredToDisplay("1987-13-13"));
		TRACE_END();
	}

	public void testCurrentDateFormatCode()
	{
		TRACE_BEGIN("testCurrentDateFormatCode");
		assertEquals("MM/dd/yyyy", appWithServer.getCurrentDateFormatCode());
		appWithServer.setCurrentDateFormatCode("dd.MM.yyyy");
		assertEquals("dd.MM.yyyy", appWithServer.getCurrentDateFormatCode());
		appWithServer.setCurrentDateFormatCode("MM/dd/yyyy");
		assertEquals("MM/dd/yyyy", appWithServer.getCurrentDateFormatCode());
		TRACE_END();
	}

	public void testMonthLabels()
	{
		TRACE_BEGIN("testMonthLabels");
		assertEquals("Mar", appWithServer.getMonthLabel("mar"));
		String[] months = appWithServer.getMonthLabels();
		assertEquals("Jan", months[0]);
		appWithServer.setCurrentLanguage("es");
		months = appWithServer.getMonthLabels();
		assertEquals("Ene", months[0]);
		appWithServer.setCurrentLanguage("en");
		TRACE_END();
	}

	public void testStatusLabels()
	{
		TRACE_BEGIN("testStatusLabels");
		assertEquals("Draft", appWithServer.getStatusLabel(Bulletin.STATUSDRAFT));
		assertEquals("Sealed", appWithServer.getStatusLabel(Bulletin.STATUSSEALED));
		TRACE_END();
	}
	
	public void testCreateFolders() throws Exception
	{
		TRACE_BEGIN("testCreateFolders");
		final int MAXFOLDERS = 10;
		appWithoutServer.setMaxNewFolders(MAXFOLDERS);
		assertNotNull("New Folder is null?", appWithoutServer.createUniqueFolder());
		assertNotNull("Could not find first new folder", appWithoutServer.store.findFolder("New Folder"));
		
		for(int i = 1; i < MAXFOLDERS; ++i)
		{
			assertNotNull("Folder"+i+" is null?", appWithoutServer.createUniqueFolder());
			assertNotNull("Could not find new folder"+i, appWithoutServer.store.findFolder("New Folder"+i));
		}
		assertNull("Max Folders reached, why is this not null?", appWithoutServer.createUniqueFolder());
		assertNull("Found this folder"+MAXFOLDERS, appWithoutServer.store.findFolder("New Folder"+MAXFOLDERS));
		TRACE_END();
	}
	
	public void testFormatPublicCode() throws Exception
	{
		TRACE_BEGIN("testCreateFolders");
		String clientId = appWithoutServer.getAccountId();
		assertNotNull("clientId Null?", clientId);
		String publicCode = MartusUtilities.computePublicCode(clientId);
		assertNotNull("publicCode Null?", publicCode);
		String formattedCode = MartusUtilities.formatPublicCode(publicCode);
		assertNotEquals("formatted code is the same as the public code?", formattedCode, publicCode);
		assertEquals("Not formatted correctly", "1234.5678.9012.3456", MartusUtilities.formatPublicCode("1234567890123456"));
		TRACE_END();
		
	}
	
	public void testShouldShowSealedUploadReminderOnExit() throws Exception
	{
		TRACE_BEGIN("testShouldShowSealedUploadReminderOnExit");
		File file = appWithServer.getUploadInfoFile();
		file.delete();
		BulletinStore store = appWithServer.getStore();

		store.deleteAllData();
		BulletinFolder outbox = appWithServer.getFolderOutbox();
		assertEquals("Outbox not empty on exit", 0, outbox.getBulletinCount());
		assertEquals("No file and outbox empty on exit", false, 
			appWithServer.shouldShowSealedUploadReminderOnExit());

		Bulletin b = appWithServer.createBulletin();
		b.save();
		store.addBulletinToFolder(b.getUniversalId(), outbox);
		assertEquals("File got created somehow on exit?", false, file.exists());
		assertEquals("Outbox empty on exit", 1, outbox.getBulletinCount());
		assertEquals("No file and outbox contains data on exit", true, 
						appWithServer.shouldShowSealedUploadReminderOnExit());

		TRACE_END();
	}

	public void testShouldShowDraftUploadReminder() throws Exception
	{
		TRACE_BEGIN("testShouldShowDraftUploadReminder");
		File file = appWithServer.getUploadInfoFile();
		file.delete();
		BulletinStore store = appWithServer.getStore();

		store.deleteAllData();
		BulletinFolder draftOutbox = appWithServer.getFolderDraftOutbox();
		assertEquals("Draft outbox not empty", 0, draftOutbox.getBulletinCount());
		assertEquals("No file and draft outbox empty", false, appWithServer.shouldShowDraftUploadReminder());

		Bulletin b = appWithServer.createBulletin();
		b.save();
		store.addBulletinToFolder(b.getUniversalId(), draftOutbox);
		assertEquals("Draft file got created somehow?", false, file.exists());
		assertEquals("Draft outbox empty", 1, draftOutbox.getBulletinCount());
		assertEquals("No file and draft outbox contains data", true, appWithServer.shouldShowDraftUploadReminder());
		TRACE_END();
	}

	public void testUploadInfo()
	{
		TRACE_BEGIN("testUploadInfo");
		File file = appWithServer.getUploadInfoFile();
		file.delete();
		assertEquals("getLastUploadedTime invalid", null, appWithServer.getLastUploadedTime());
		assertEquals("getLastUploadRemindedTime invalid", null, appWithServer.getLastUploadRemindedTime());

		Date d1 = new Date();
		appWithServer.setLastUploadedTime(d1);
		assertEquals("getLastUploadedTime not d1", d1, appWithServer.getLastUploadedTime());
		assertEquals("getLastUploadRemindedTime not null", null, appWithServer.getLastUploadRemindedTime());

		Date d2 = new Date(d1.getTime()+100);
		appWithServer.setLastUploadRemindedTime(d2);
		assertEquals("getLastUploadedTime not d1", d1, appWithServer.getLastUploadedTime());
		assertEquals("getLastUploadRemindedTime not d2", d2, appWithServer.getLastUploadRemindedTime());

		Date d3 = new Date(d2.getTime()+100);
		appWithServer.setLastUploadedTime(d3);
		assertEquals("getLastUploadedTime not d3", d3, appWithServer.getLastUploadedTime());
		assertEquals("getLastUploadRemindedTime not d2", d2, appWithServer.getLastUploadRemindedTime());

		file.delete();
		Date d4 = new Date(d3.getTime()+100);
		appWithServer.setLastUploadRemindedTime(d4);
		assertEquals("getLastUploadedTime not null", null, appWithServer.getLastUploadedTime());
		assertEquals("getLastUploadRemindedTime not d4", d4, appWithServer.getLastUploadRemindedTime());

		TRACE_END();
	}

	public void testResetUploadInfo()
	{
		TRACE_BEGIN("testResetUploadInfo");
		File file = appWithServer.getUploadInfoFile();

		file.delete();
		appWithServer.resetLastUploadedTime();
		long uploadedTime = appWithServer.getLastUploadedTime().getTime();
		long currentTime1 = System.currentTimeMillis();
		assertTrue("CurrentTime1 < uploadTime", currentTime1 >= uploadedTime);
		assertTrue("ResetLastUploadedTime", currentTime1 - uploadedTime <= 1000 );

		file.delete();
		appWithServer.resetLastUploadRemindedTime();
		long remindedTime = appWithServer.getLastUploadRemindedTime().getTime();
		long currentTime2 = System.currentTimeMillis();
		assertTrue("CurrentTime2 < remindedTime", currentTime2 >= remindedTime);
		assertTrue("ResetLastUploadRemindedTime", currentTime2 - remindedTime <= 1000 );
		TRACE_END();
	}
	
	public void testRepairOrphans()
	{
		assertEquals("already have orphans?", 0, appWithAccount.repairOrphans());
		assertNull("Orphan Folder exists?", appWithAccount.getStore().findFolder(BulletinStore.RECOVERED_BULLETIN_FOLDER));
		Bulletin b1 = appWithAccount.createBulletin();
		b1.save();
		assertEquals("didn't find the orphan?", 1, appWithAccount.repairOrphans());
		assertEquals("didn't fix the orphan?", 0, appWithAccount.repairOrphans());
		
		BulletinFolder orphanFolder = appWithAccount.getStore().findFolder(BulletinStore.RECOVERED_BULLETIN_FOLDER);
		assertEquals("where did the orphan go?", 1, orphanFolder.getBulletinCount());
		assertTrue("wrong bulletin?", orphanFolder.contains(b1));
		
		appWithAccount.loadFolders();
		BulletinFolder orphanFolder2 = appWithAccount.getStore().findFolder(BulletinStore.RECOVERED_BULLETIN_FOLDER);
		assertEquals("forgot to save folders?", 1, orphanFolder2.getBulletinCount());
	}
	
	public void testSetBulletinHQKey() throws Exception
	{
		String key = "aabcc";
		appWithAccount.setHQKey(key);

		Bulletin b1 = appWithAccount.createBulletin();
		assertEquals("key already set?", "", b1.getHQPublicKey());
		appWithAccount.setHQKeyInBulletin(b1);
		assertEquals("Key not set?", key, b1.getHQPublicKey());
	}

	private Bulletin createSealedBulletin(MartusApp app)
	{
		Bulletin b = app.createBulletin();
		b.setSealed();
		b.set(Bulletin.TAGTITLE, "test title");
		b.save();
		app.getFolderOutbox().add(b);
		return b;
	}

	private Bulletin createDraftBulletin(MartusApp app)
	{
		Bulletin b = app.createBulletin();
		b.setDraft();
		b.set(Bulletin.TAGTITLE, "test title");
		b.save();
		app.getFolderDraftOutbox().add(b);
		return b;
	}

	public class MockServerInterfaceHandler extends ServerSideNetworkHandler
	{
		MockServerInterfaceHandler(MartusServer serverToUse)
		{
			super(serverToUse);
		}
		
		public void nullGetFieldOfficeAccountIds(boolean shouldReturnNull)
		{
			nullGetFieldOfficeAccountIds = shouldReturnNull;
		}
		
		public Vector getFieldOfficeAccountIds(String myAccountId, Vector parameters, String signature)
		{
			if(nullGetFieldOfficeAccountIds)
				return null;
			return super.getFieldOfficeAccountIds(myAccountId, parameters, signature);
		}
		
		boolean nullGetFieldOfficeAccountIds;
	}
	
	public static class MockServerNotAvailable extends MockMartusServer
	{
		MockServerNotAvailable() throws Exception
		{
			super();
		}

		public String ping()
		{
			return null;
		}
		
	}

	Bulletin createAndUploadSampleBulletin() throws Exception
	{
		BulletinStore store = appWithAccount.getStore();
		mockServer.allowUploads(appWithAccount.getAccountId());
		Bulletin b2 = appWithAccount.createBulletin();
		b2.setSealed();
		b2.save();
		assertEquals("upload b2", NetworkInterfaceConstants.OK, appWithAccount.uploadBulletin(b2, null));
		store.destroyBulletin(b2);
		return b2;
	}

	byte[] getBulletinZipBytes(Bulletin b) throws Exception
	{
		return Base64.decode(MockBulletin.saveToZipString(b));
	}
		
	private void TRACE_BEGIN(String method)
	{
		if(VERBOSE)
		{
			System.out.print("TestMartusApp." + method + ": ");
			methodStartedAt = System.currentTimeMillis();
		}
	}

	private void TRACE_END()
	{
		if(VERBOSE)
			System.out.println(System.currentTimeMillis() - methodStartedAt);
	}

	private static MockMartusSecurity mockSecurityForApp;
	private static MockMartusSecurity mockSecurityForServer;

	private MockMartusApp appWithServer;
	private MockMartusApp appWithoutServer;
	private MockMartusApp appWithAccount;

	private MockMartusServer mockServer;
	private NetworkInterfaceForNonSSL mockNonSSLServerHandler;
	private MockServerInterfaceHandler mockSSLServerHandler;
	
	private long methodStartedAt;
}

