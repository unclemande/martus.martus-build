/*

The Martus(tm) free, social justice documentation and
monitoring software. Copyright (C) 2002, Beneficent
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

package org.martus.client.core;

import java.io.IOException;
import java.io.Writer;

import org.martus.common.MartusUtilities;
import org.martus.common.MartusXml;

public class BulletinXmlExporter
{
	public BulletinXmlExporter()
	{
		super();
	}

	public static void export(Bulletin b, Writer dest) throws IOException
	{
		dest.write(MartusXml.getTagStart(ExportedBulletinsElementName));
		dest.write("\n\n");

		dest.write(MartusXml.getTagStart(BulletinElementName));
		dest.write("\n");

		writeElement(dest, LocalIdElementName, b.getLocalId());
		writeElement(dest, AccountIdElementName, b.getAccount());
		String[] tags = Bulletin.getStandardFieldNames();
		writeFields(dest, b, tags);

		dest.write(MartusXml.getTagEnd(BulletinElementName));
		dest.write("\n");

		dest.write(MartusXml.getTagEnd(ExportedBulletinsElementName));
	}

	static void writeFields(Writer dest, Bulletin b, String[] tags)
		throws IOException
	{
		for (int i = 0; i < tags.length; i++)
		{
			String tag = tags[i];
			String rawFieldData = b.get(tag);
			writeElement(dest, tag, rawFieldData);
		}
	}

	static void writeElement(Writer dest, String tag, String rawFieldData) throws IOException
	{
		dest.write(MartusXml.getTagStart(tag));
		dest.write(MartusUtilities.getXmlEncoded(rawFieldData));
		dest.write(MartusXml.getTagEnd(tag));
	}
	
	public final static String ExportedBulletinsElementName = "ExportedMartusBulletins";
	public final static String BulletinElementName = "MartusBulletin";
	public final static String LocalIdElementName = "LocalId";
	public final static String AccountIdElementName = "AuthorAccountId";
}
