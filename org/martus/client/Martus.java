package org.martus.client;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

import org.martus.client.*;

import MartusJava.TestAll;

import java.util.Arrays;

class Martus
{
    public static void main (String args[])
	{
		if(args.length >0)
		{
			if(args.length == 1 && args[0].compareToIgnoreCase("-testall")==0)
			{
				TestAll.runTests();
				System.exit(0);
			}
			else
			{
				System.out.println("Incorrect command line parameter");
				System.out.println("The only valid optional commands is:");
				System.out.println("-testall");
				System.exit(1);
			}
		}
		try
		{
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch(Exception e)
		{
			System.out.println(e);
			//e.printStatckTrace(System.out);
		}
        UiMainWindow window = new UiMainWindow();
        if(!window.run())
        	System.exit(0);
    }
}
