package com.leafdigital.loadtestapplet;

import java.applet.Applet;
import java.awt.*;
import java.io.*;
import java.lang.reflect.Method;
import java.net.*;
import java.util.*;
import java.util.regex.*;

import javax.swing.*;

/**
 * Applet that can be controlled from JavaScript to carry out load testing by
 * making many web requests.
 */
public class LoadTestApplet extends JApplet implements ThreadHandler.Reporter,
	WorkScheduler.Handler
{
	static final Color LIGHT_COLOR = new Color(255, 240, 245);
	static final Color DARK_COLOR = new Color(128, 20, 100);

	private final static int READ_TIMEOUT = 10000, CONNECT_TIMEOUT = 10000;

	private final static int DEFAULT_THREADS = 20;
	private final static int DEFAULT_SCALE = 1000;
	private final static String CONSOLE_TAG = "leafdigital LoadTestApplet: ";

	private final static int MARGIN = 10;

	private int maxThreads, scale;
	private String cookie;

	private Object threadsSynch = new Object();
	private ThreadHandler[] threads;
	private ThreadTracker tracker;
	private WorkScheduler scheduler;

	private int nextThread;

	private JProgressBar progress;
	private JPanel threadDisplay;

	@Override
	public void init()
	{
		super.init();

		// Set L&F
		try
		{
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch(Exception t)
		{
			System.err.println(CONSOLE_TAG + "ERR001 Failed to set L&F");
			t.printStackTrace();
			System.err.println();
		}

		// Get number of threads
		maxThreads = DEFAULT_THREADS;
		String threadsText = getParameter("maxThreads");
		if(threadsText != null && threadsText.matches("[1-9][0-9]{0,3}"))
		{
			maxThreads = Integer.parseInt(threadsText);
		}

		// Get scale
		scale = DEFAULT_SCALE;
		String scaleText = getParameter("scale");
		if(scaleText != null && threadsText.matches("[1-9][0-9]{2,9}"))
		{
			scale = Integer.parseInt(scaleText);
		}

		// Create thread objects
		tracker = new ThreadTracker();
		threads = new ThreadHandler[0];

		scheduler = new WorkScheduler(this);

		// Get cookie to use (full value of header line)
		cookie = getParameter("cookie");

		// Set up display
		JLabel title = new JLabel("leafdigital LoadTestApplet");
		title.setForeground(Color.BLACK);
		progress = new JProgressBar(JProgressBar.HORIZONTAL, 0, 100);
		progress.setEnabled(false);

		// Top panel: title, progress
		JPanel top = new JPanel(new BorderLayout(MARGIN, MARGIN));
		top.setBackground(Color.WHITE);
		getContentPane().setLayout(new BorderLayout(MARGIN, MARGIN));
		getContentPane().setBackground(Color.WHITE);
		getContentPane().add(top, BorderLayout.NORTH);

		top.add(title, BorderLayout.WEST);
		top.add(progress, BorderLayout.CENTER);

		// Remainder consists of thread display
		threadDisplay = new JPanel(new FlowLayout(FlowLayout.LEADING,
			MARGIN, MARGIN));
		threadDisplay.setBackground(Color.WHITE);
		threadDisplay.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1,
			DARK_COLOR));
		getContentPane().add(threadDisplay, BorderLayout.CENTER);
	}

	@Override
	public void start()
	{
		// Start all the threads
		tracker.start();
	}

	@Override
	public void stop()
	{
		// Destroy all current requests
		for(ThreadHandler handler : threads)
		{
			handler.stop();
		}
		tracker.stop();

		WorkScheduler exScheduler;
		synchronized(this)
		{
			exScheduler = scheduler;
			scheduler = null;
		}
		exScheduler.stop();

		super.stop();
	}

	@Override
	public void destroy()
	{
		// Chuck all the thread handlers
		threads = null;

		// Remove all the components (just so this pairs init)
		for(Component component :	getContentPane().getComponents())
		{
			getContentPane().remove(component);
		}

		super.destroy();
	}

	@Override
	public void succeeded(int index, int ms)
	{
		report(index, ms, true);
	}

	@Override
	public void failed(int index, int ms)
	{
		report(index, ms, false);
	}

	@Override
	public void percentComplete(final int percent)
	{
		SwingUtilities.invokeLater(new Runnable()
		{
			@Override
			public void run()
			{
				progress.setValue(percent);
			}
		});
	}

	/**
	 * Reports a test result to JavaScript.
	 * @param index Index of task
	 * @param ms Milliseconds (-1 = task was not attempted because all threads
	 *   were busy)
	 * @param result True = success, false = failure
	 */
	private void report(int index, int ms, boolean result)
	{
		evalJS("loadTestResult(" + index + "," + ms + "," + result + ");");
	}

	private Object jsSynch = new Object();
	private LinkedList<String> jsList;

	private void evalJS(String js)
	{
		// Use sync to ensure that commands are run in order
		synchronized(jsSynch)
		{
			if(jsList == null)
			{
				jsList = new LinkedList<String>();
				SwingUtilities.invokeLater(new Runnable()
				{
					@Override
					public void run()
					{
						while(true)
						{
							String js;
							synchronized(jsSynch)
							{
								if(jsList.isEmpty())
								{
									jsList = null;
									break;
								}
								else
								{
									js = jsList.removeFirst();
								}
							}
							try
							{
								// Decided to use reflection to make this easier to compile - otherwise
								// it needs plugin.jar from a JRE. Also this should make it safer at
								// runtime.

								// JSObject.getWindow(this).eval(js);
								Class<?> c=Class.forName("netscape.javascript.JSObject");
								Method m = c.getMethod("getWindow", new Class<?>[] {Applet.class});
								Object win = m.invoke(null, (java.applet.Applet)LoadTestApplet.this);
								Method m2 = c.getMethod("eval", new Class<?>[] {String.class});
								m2.invoke(win, js);
							}
							catch (ClassNotFoundException ex)
							{
								System.err.println(CONSOLE_TAG + "JSObject support not found - " + js);
							}
							catch(Exception ex)
							{
								ex.printStackTrace();
							}
						}
					}
				});
			}
			jsList.addLast(js);
		}
	}

	@Override
	public void runTask(int index, Runnable task)
	{
		// Try to reuse existing threads
		int end = nextThread;
		do
		{
			ThreadHandler thread;
			synchronized(threadsSynch)
			{
				if(threads.length == 0)
				{
					break;
				}
				if(nextThread >= threads.length)
				{
					nextThread = 0;
				}
				thread = threads[nextThread];
				nextThread++;
			}
			if(thread.task(index, task))
			{
				return;
			}
		}
		while(nextThread != end);

		// Add another thread
		synchronized(threadsSynch)
		{
			if(threads.length < maxThreads)
			{
				ThreadHandler[] newThreads = new ThreadHandler[threads.length + 1];
				System.arraycopy(threads, 0, newThreads, 0, threads.length);
				final ThreadHandler newHandler = new ThreadHandler(threads.length, this,
					tracker, scale);
				newThreads[threads.length] = newHandler;
				newHandler.start();
				SwingUtilities.invokeLater(new Runnable()
				{
					@Override
					public void run()
					{
						threadDisplay.add(newHandler.getComponent());
						threadDisplay.revalidate();
					}
				});
				threads = newThreads;
				if(newHandler.task(index, task))
				{
					return;
				}
			}
		}

		report(index, -1, false);
	}

	@Override
	public void workFinished()
	{
		// OK the scheduled work has finished, BUT we have to wait for all the
		// threads to finish too.
		for(ThreadHandler thread : threads)
		{
			thread.waitForIdle();
		}
		evalJS("loadTestFinished();");
		progressBarClear();
	}

	/**
	 * Resets ready for a new load test.
	 * <p>
	 * Called from JS.
	 */
	public void loadTestReset()
	{
		WorkScheduler exScheduler;
		synchronized(this)
		{
			exScheduler = scheduler;
			scheduler = new WorkScheduler(this);
		}
		synchronized(threadsSynch)
		{
			for(final ThreadHandler thread : threads)
			{
				thread.stop();
				SwingUtilities.invokeLater(new Runnable()
				{
					@Override
					public void run()
					{
						threadDisplay.remove(thread.getComponent());
						threadDisplay.revalidate();
						threadDisplay.repaint();
					}
				});
			}
			threads = new ThreadHandler[0];
		}
		exScheduler.stop();
		progressBarClear();
	}

	/**
	 * Clears progress bar.
	 */
	private void progressBarClear()
	{
		SwingUtilities.invokeLater(new Runnable()
		{
			@Override
			public void run()
			{
				progress.setValue(0);
				progress.setEnabled(false);
			}
		});
	}

	/**
	 * Starts the pre-programmed load test. The load test will continue until
	 * either loadTestFinished() is sent (successful completion), or somebody
	 * calls loadTestRest() (cancel), or possibly some error.
	 * <p>
	 * Called from JS.
	 */
	public void loadTestStart()
	{
		synchronized(this)
		{
			scheduler.start();
		}
		SwingUtilities.invokeLater(new Runnable()
		{
			@Override
			public void run()
			{
				progress.setEnabled(true);
			}
		});
	}

	/**
	 * Adds a new load test event to the schedule.
	 * <p>
	 * Called from JS.
	 * @param time Time to run the event
	 * @param url URL to request
	 * @param match Regular expression which must be found in result
	 * @return Index of event (first is 0)
	 * @throws IllegalArgumentException If URL is not valid
	 * @throws PatternSyntaxException If regex is not valid
	 */
	public int loadTestEvent(int time, String url, String match)
		throws IllegalArgumentException, PatternSyntaxException
	{
		Runnable task;
		try
		{
			task = new LoadTestTask(new URL(url), Pattern.compile(match));
		}
		catch(MalformedURLException e)
		{
			throw new IllegalArgumentException(e);
		}
		synchronized(this)
		{
			return scheduler.addTask(time, task);
		}
	}

	/**
	 * Sets the cookie to use for all requests. Must be called before test
	 * is started.
	 * <p>
	 * Called from JS.
	 * @param cookie Cookie (value of header)
	 */
	public void loadTestCookie(String cookie)
	{
		if(cookie.equals(""))
		{
			this.cookie = null;
		}
		else
		{
			this.cookie = cookie;
		}
	}

	private class LoadTestTask implements Runnable
	{
		private URL url;
		private Pattern match;

		/**
		 * @param url URL to request
		 * @param match Regular expression which must be found in result
		 */
		public LoadTestTask(URL url, Pattern match)
		{
			this.url = url;
			this.match = match;
		}

		@Override
		public void run()
		{
			try
			{
				HttpURLConnection connection = (HttpURLConnection)url.openConnection();
				connection.setConnectTimeout(CONNECT_TIMEOUT);
				connection.setReadTimeout(READ_TIMEOUT);
				connection.setRequestProperty("User-Agent", "leafdigital-LoadTestApplet/1.0");
				if(cookie != null)
				{
					connection.setRequestProperty("Cookie", cookie);
				}

				String encoding = connection.getContentEncoding();
				if(encoding == null)
				{
					encoding = "US-ASCII";
				}

				ByteArrayOutputStream output = new ByteArrayOutputStream();
				InputStream in = connection.getInputStream();
				byte[] buffer = new byte[65536];
				while(true)
				{
					int read = in.read(buffer);
					if(read == -1)
					{
						break;
					}
					output.write(buffer, 0, read);
				}
				in.close();
				String text = output.toString(encoding);

				if(!match.matcher(text).find())
				{
					throw new Error("Did not match");
				}
			}
			catch(IOException e)
			{
				e.printStackTrace();
				throw new Error(e);
			}
		}
	}
}
