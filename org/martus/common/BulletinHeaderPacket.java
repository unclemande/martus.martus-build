package org.martus.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class BulletinHeaderPacket extends Packet
{
	public BulletinHeaderPacket(String accountString)
	{
		super(createUniversalId(accountString));
		initialize();
	}
	
	public BulletinHeaderPacket(UniversalId universalIdToUse)
	{
		super(universalIdToUse);
		initialize();
	}
	
	public static UniversalId createUniversalId(String accountId)
	{
		return UniversalId.createFromAccountAndPrefix(accountId, prefix);
	}
	
	public static boolean isValidLocalId(String localId)
	{
		return localId.startsWith(prefix);
	}
	
	public void clearAttachments()
	{
		publicAttachments.clear();
		privateAttachments.clear();
	}
	
	public void setStatus(String newStatus)
	{
		status = newStatus;
	}
	
	public String getStatus()
	{
		return status;
	}
	
	public long getLastSavedTime()
	{
		return lastSavedTime;
	}
	
	public void updateLastSavedTime()
	{
		lastSavedTime = System.currentTimeMillis();
	}
	
	public void setFieldDataPacketId(String id)
	{
		fieldDataPacketId = id;
	}

	public String getFieldDataPacketId()
	{
		return fieldDataPacketId;
	}
	
	public void setPrivateFieldDataPacketId(String id)
	{
		privateFieldDataPacketId = id;
	}

	public String getPrivateFieldDataPacketId()
	{
		return privateFieldDataPacketId;
	}

	public String getHQPublicKey()
	{
		return hqPublicKey;	
	}

	public void setHQPublicKey(String key)
	{
		hqPublicKey = key;	
	}
	
	public void setAllPrivate(boolean newValue)
	{
		allPrivate = newValue;
	}
	
	public boolean isAllPrivate()
	{
		return allPrivate;
	}
	
	public void setFieldDataSignature(byte[] sig)
	{
		fieldDataPacketSig = sig;
	}
	
	public byte[] getFieldDataSignature()
	{
		return fieldDataPacketSig;
	}

	public void setPrivateFieldDataSignature(byte[] sig)
	{
		privateFieldDataPacketSig = sig;
	}

	public byte[] getPrivateFieldDataSignature()
	{
		return privateFieldDataPacketSig;
	}

	public String[] getPublicAttachmentIds()
	{
		String[] result = new String[publicAttachments.size()];
		for(int i = 0; i < result.length; ++i)
			result[i] = (String)publicAttachments.get(i);
		
		Arrays.sort(result);
		return result;
	}

	public String[] getPrivateAttachmentIds()
	{
		String[] result = new String[privateAttachments.size()];
		for(int i = 0; i < result.length; ++i)
			result[i] = (String)privateAttachments.get(i);
		
		Arrays.sort(result);
		return result;
	}
	
	public void addPublicAttachmentLocalId(String id)
	{
		if(publicAttachments.contains(id))
			return;
		publicAttachments.add(id);
	}
	
	public void addPrivateAttachmentLocalId(String id)
	{
		if(privateAttachments.contains(id))
			return;
		privateAttachments.add(id);
	}
	
	public void removeAllPublicAttachments()
	{
		publicAttachments.clear();
	}

	public void removeAllPrivateAttachments()
	{
		privateAttachments.clear();
	}
	
	public void loadFromXml(InputStreamWithSeek inputStream, byte[] expectedSig, MartusCrypto verifier) throws 
		IOException,
		InvalidPacketException,
		WrongPacketTypeException,
		SignatureVerificationException,
		MartusCrypto.DecryptionException,
		MartusCrypto.NoKeyPairException
	{
		super.loadFromXmlInternal(inputStream, expectedSig, verifier);
	}

	public void loadFromXml(InputStreamWithSeek inputStream, MartusCrypto verifier) throws 
		IOException,
		InvalidPacketException,
		WrongPacketTypeException,
		SignatureVerificationException,
		MartusCrypto.DecryptionException,
		MartusCrypto.NoKeyPairException
	{
		loadFromXml(inputStream, null, verifier);
	}

	public static BulletinHeaderPacket loadFromZipFile(ZipFile zip, MartusCrypto verifier)
		throws IOException,
		SignatureVerificationException 
	{
		BulletinHeaderPacket header = new BulletinHeaderPacket("Unknown");
		ZipEntry headerZipEntry = getBulletinHeaderEntry(zip);

		InputStreamWithSeek headerIn = new ZipEntryInputStream(zip, headerZipEntry);
		try
		{
			header.loadFromXml(headerIn, verifier);
			if(!header.getLocalId().equals(headerZipEntry.getName()))
				throw new IOException("Misnamed header entry");
		}
		catch(Packet.SignatureVerificationException e)
		{
			throw e;
		}
		catch(Exception e)
		{
			throw new IOException(e.getMessage());
		}
		finally
		{
			headerIn.close();
		}
		return header;
	}

	static ZipEntry getBulletinHeaderEntry(ZipFile zip) throws IOException
	{
		Enumeration entries = zip.entries();
		while(entries.hasMoreElements())
		{
			ZipEntry headerEntry = (ZipEntry)entries.nextElement();
			if(isValidLocalId(headerEntry.getName()))
				return headerEntry;
		}
		
		throw new IOException("Missing header entry");
	}
	
	protected String getPacketRootElementName()
	{
		return MartusXml.BulletinHeaderPacketElementName;
	}
	
	protected void internalWriteXml(XmlWriterFilter dest) throws IOException
	{
		super.internalWriteXml(dest);

		writeElement(dest, MartusXml.BulletinStatusElementName, getStatus());
		writeElement(dest, MartusXml.LastSavedTimeElementName, Long.toString(getLastSavedTime()));
		
		String allPrivateValue = ALL_PRIVATE;
		if(!isAllPrivate())
			allPrivateValue = NOT_ALL_PRIVATE;
		writeElement(dest, MartusXml.AllPrivateElementName, allPrivateValue);

		String hqPublicKey = getHQPublicKey();
		if(hqPublicKey.length() > 0)
			writeElement(dest, MartusXml.HQPublicKeyElementName, hqPublicKey);		
		
		String dataId = getFieldDataPacketId();
		if(dataId != null)
		{
			writeElement(dest, MartusXml.DataPacketIdElementName, dataId);
			writeElement(dest, MartusXml.DataPacketSigElementName, Base64.encode(fieldDataPacketSig));
		}

		String privateId = getPrivateFieldDataPacketId();
		if(privateId != null)
		{
			writeElement(dest, MartusXml.PrivateDataPacketIdElementName, privateId);
			writeElement(dest, MartusXml.PrivateDataPacketSigElementName, Base64.encode(privateFieldDataPacketSig));
		}
		
		String[] publicAttachmentIds = getPublicAttachmentIds();
		for(int i = 0; i < publicAttachmentIds.length; ++i)
		{
			writeElement(dest, MartusXml.PublicAttachmentIdElementName, publicAttachmentIds[i]);
		}

		String[] privateAttachmentIds = getPrivateAttachmentIds();
		for(int i = 0; i < privateAttachmentIds.length; ++i)
		{
			writeElement(dest, MartusXml.PrivateAttachmentIdElementName, privateAttachmentIds[i]);
		}
	}
	
	protected void setFromXml(String elementName, String data) throws 
			Base64.InvalidBase64Exception
	{
		if(elementName.equals(MartusXml.BulletinStatusElementName))
		{
			setStatus(data);
		}
		if(elementName.equals(MartusXml.LastSavedTimeElementName))
		{
			lastSavedTime = Long.parseLong(data);
		}
		else if(elementName.equals(MartusXml.AllPrivateElementName))
		{
			if(data.equals(NOT_ALL_PRIVATE))
				setAllPrivate(false);
			else
				setAllPrivate(true);
		}
		else if(elementName.equals(MartusXml.HQPublicKeyElementName))
		{
			setHQPublicKey(data);
		}
		else if(elementName.equals(MartusXml.DataPacketIdElementName))
		{
			setFieldDataPacketId(data);
		}
		else if(elementName.equals(MartusXml.PrivateDataPacketIdElementName))
		{
			setPrivateFieldDataPacketId(data);
		}
		else if(elementName.equals(MartusXml.DataPacketSigElementName))
		{
			setFieldDataSignature(Base64.decode(data));
		}
		else if(elementName.equals(MartusXml.PrivateDataPacketSigElementName))
		{
			setPrivateFieldDataSignature(Base64.decode(data));
		}
		else if(elementName.equals(MartusXml.PublicAttachmentIdElementName))
		{
			addPublicAttachmentLocalId(data);
		}
		else if(elementName.equals(MartusXml.PrivateAttachmentIdElementName))
		{
			addPrivateAttachmentLocalId(data);
		}
		else
		{
			super.setFromXml(elementName, data);
		}
	}
	
	protected void initialize()
	{
		status = "";
		allPrivate = true;
		fieldDataPacketSig = new byte[1];
		privateFieldDataPacketSig = new byte[1];
		publicAttachments = new Vector();
		privateAttachments = new Vector();
		hqPublicKey = "";
		lastSavedTime = TIME_UNKNOWN;
	}
	
	private final static String ALL_PRIVATE = "1";
	private final static String NOT_ALL_PRIVATE = "0";
	
	public static final long TIME_UNKNOWN = 0;

	boolean allPrivate;	
	String fieldDataPacketId;
	String privateFieldDataPacketId;
	String status;
	String hqPublicKey;
	private long lastSavedTime;
	private byte[] fieldDataPacketSig;
	private byte[] privateFieldDataPacketSig;
	private Vector publicAttachments;
	private Vector privateAttachments;
	private static final String prefix = "B-";
}
