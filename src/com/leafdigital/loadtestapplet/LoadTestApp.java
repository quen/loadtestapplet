package com.leafdigital.loadtestapplet;

import java.applet.*;
import java.awt.BorderLayout;
import java.awt.event.*;
import java.net.URL;

import javax.swing.*;

/**
 * For testing the applet from inside a convenient app.
 */
public class LoadTestApp extends JFrame implements AppletStub
{
	LoadTestApp()
	{
		super("LoadTest applet");
		getContentPane().setLayout(new BorderLayout());
		final LoadTestApplet applet = new LoadTestApplet();

		applet.setStub(this);
		applet.init();
		applet.start();
		getContentPane().add(applet, BorderLayout.CENTER);
		setDefaultCloseOperation(EXIT_ON_CLOSE);

		final JTextField url = new JTextField("http://");
		final JTextField expected = new JTextField("expected");
		JButton button = new JButton("Test");
		JPanel lower = new JPanel(new BorderLayout(8, 8));
		getContentPane().add(lower, BorderLayout.SOUTH);
		lower.add(button, BorderLayout.EAST);
		JPanel left = new JPanel(new BorderLayout(8, 8));
		lower.add(left, BorderLayout.CENTER);
		left.add(url, BorderLayout.CENTER);
		left.add(expected, BorderLayout.EAST);

		button.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				String urlString = url.getText();
				String expectedPattern = expected.getText();
				applet.loadTestReset();
				for(int i=500; i<=5000; i+=100)
				{
					applet.loadTestEvent(i, urlString, expectedPattern);
				}
				applet.loadTestStart();
			}
		});

		setSize(600, 400);
		setVisible(true);
	}

	/**
	 * Launches app for testing.
	 * @param args
	 */
	public static void main(String[] args)
	{
		SwingUtilities.invokeLater(new Runnable()
		{
			/**
			 * Initialises app on AWT thread.
			 */
			@Override
			public void run()
			{
				new LoadTestApp();
			}
		});
	}

	@Override
	public URL getDocumentBase()
	{
		return null;
	}

	@Override
	public URL getCodeBase()
	{
		return null;
	}

	@Override
	public String getParameter(String name)
	{
		return null;
	}

	@Override
	public AppletContext getAppletContext()
	{
		return null;
	}

	@Override
	public void appletResize(int width, int height)
	{
	}
}
