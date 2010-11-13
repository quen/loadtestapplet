package com.leafdigital.loadtestapplet;

import javax.swing.JComponent;

/**
 * Groups all the components necessary to handle a thread.
 */
public class ThreadHandler
{
	private int index;
	private boolean started, stop, stopped;
	private Runnable currentTask;
	private int taskIndex;
	private long currentStart;

	private int lastTime, beforeTime;

	private Reporter reporter;
	private ThreadDisplay threadDisplay;
	private ThreadTracker tracker;

	/**
	 * Called to report progress (success or failure).
	 */
	public interface Reporter
	{
		/**
		 * A task completed in the given time.
		 * @param taskIndex Task index
		 * @param ms Milliseconds
		 */
		public void succeeded(int taskIndex, int ms);

		/**
		 * A task failed in the given time.
		 * @param taskIndex Task index
		 * @param ms Milliseconds
		 */
		public void failed(int taskIndex, int ms);
	}

	/**
	 * @param index Index of this thread
	 * @param reporter Reporter for task results
	 * @param tracker Tracker for thread management
	 * @param scale Scale for boxes (max milliseconds to display)
	 */
	public ThreadHandler(int index, Reporter reporter, ThreadTracker tracker, int scale)
	{
		this.index = index;
		this.reporter = reporter;
		this.tracker = tracker;
		this.threadDisplay = new ThreadDisplay(this, scale);
	}

	/**
	 * @return Index of thread handler
	 */
	public int getIndex()
	{
		return index;
	}

	/**
	 * Initialises thread (must call before it does anything)
	 * @throws IllegalStateException If already started
	 */
	public synchronized void start() throws IllegalStateException
	{
		if(started)
		{
			throw new IllegalStateException("Already started");
		}
		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				thread();
			}
		}, "Load test thread " + index).start();
	}

	private void thread()
	{
		try
		{
			while(true)
			{
				Runnable task;
				long startTime;
				synchronized(this)
				{
					while(currentTask == null && !stop)
					{
						wait();
					}
					if(stop)
					{
						break;
					}
					task = currentTask;
					startTime = System.currentTimeMillis();
					currentStart = startTime;
				}
				tracker.startActivity(this);
				try
				{
					task.run();
					beforeTime = lastTime;
					lastTime = (int)(System.currentTimeMillis() - currentStart);
					reporter.succeeded(taskIndex, lastTime);
				}
				catch(Throwable e)
				{
					beforeTime = lastTime;
					lastTime = (int)(System.currentTimeMillis() - currentStart);
					reporter.failed(taskIndex, lastTime);
				}
				finally
				{
					tracker.stopActivity(this);
					synchronized(this)
					{
						currentTask = null;
						notifyAll();
					}
				}
			}
		}
		catch(InterruptedException e)
		{
			// wtf
		}
		finally
		{
			synchronized(this)
			{
				stopped = true;
				notifyAll();
			}
		}
	}

	/**
	 * Waits until this handler has finished its task.
	 */
	public synchronized void waitForIdle()
	{
		while(currentTask != null)
		{
			try
			{
				wait();
			}
			catch(InterruptedException e)
			{
			}
		}
	}

	/**
	 * @return Tine (milliseconds) into current task or 0 if no current task
	 */
	public synchronized int getThisTime()
	{
		if(currentTask != null)
		{
			return (int)(System.currentTimeMillis() - currentStart);
		}
		else
		{
			return 0;
		}
	}

	/**
	 * @return Time (milliseconds) for last request or 0 if none
	 */
	public synchronized int getLastTime()
	{
		return lastTime;
	}

	/**
	 * @return Time (milliseconds) for request before last or 0 if none
	 */
	public synchronized int getBeforeTime()
	{
		return beforeTime;
	}

	/**
	 * Runs a task on this thread handler if there is no current task.
	 * @param index Task index
	 * @param runnable Task
	 * @return True if this task is now running, false if thread is busy
	 */
	public synchronized boolean task(int index, Runnable runnable)
	{
		if(currentTask != null)
		{
			return false;
		}
		currentTask = runnable;
		taskIndex = index;
		currentStart = 0;
		notifyAll();
		return true;
	}

	/**
	 * Stops handler thread; does not return until thread is stopped
	 */
	public synchronized void stop()
	{
		stop = true;
		notifyAll();
		try
		{
			while(!stopped)
			{
				wait();
			}
		}
		catch(InterruptedException e)
		{
		}
	}

	/**
	 * @return Display component for thread
	 */
	public JComponent getComponent()
	{
		return threadDisplay;
	}

	/**
	 * Called by tracker to update display
	 */
	public void tick()
	{
		threadDisplay.update();
	}
}
