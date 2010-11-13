package com.leafdigital.loadtestapplet;

import java.util.*;

/**
 * Manages the list of tasks for a specific test run. When the test run starts,
 * it triggers all these tasks (if possible) at the relevant times.
 */
public class WorkScheduler
{
	private SortedSet<TaskTime> work = new TreeSet<TaskTime>();
	private boolean started, stop, stopped;
	private int index = 0;
	private Handler handler;

	private final static int ASSUMED_TASK_LENGTH = 1000;

	/**
	 * @param handler Handler for tasks
	 */
	public WorkScheduler(Handler handler)
	{
		this.handler = handler;
	}

	/** Interface for owner object. */
	public interface Handler
	{
		/**
		 * Called when we are a certain percentage through the specified task time.
		 * @param percent Percentage complete
		 */
		public void percentComplete(int percent);

		/**
		 * Called to request that the task should be run now.
		 * @param index Index
		 * @param task Task to run
		 */
		public void runTask(int index, Runnable task);

		/**
		 * Called to indicate that the test has completed.
		 */
		public void workFinished();
	}

	private static class TaskTime implements Comparable<TaskTime>
	{
		private Runnable task;
		private int time, index;

		private TaskTime(int index, Runnable task, int time)
		{
			this.index = index;
			this.task = task;
			this.time = time;
		}

		@Override
		public int compareTo(TaskTime o)
		{
			if(time < o.time)
			{
				return -1;
			}
			else if(time > o.time)
			{
				return 1;
			}
			else
			{
				// Arbitrary but consistent order
				return index - o.index;
			}
		}

		/**
		 * @return Time in milliseconds from test start to run this task
		 */
		public int getTime()
		{
			return time;
		}

		/**
		 * @return Task to run
		 */
		public Runnable getTask()
		{
			return task;
		}

		/**
		 * @return Index of task
		 */
		public int getIndex()
		{
			return index;
		}
	}

	/**
	 * Adds a new task to the schedule.
	 * @param time Time in milliseconds since test start
	 * @param task Task to run
	 * @return Index for task
	 * @throws IllegalStateException If already started
	 */
	public synchronized int addTask(int time, Runnable task)
		throws IllegalStateException
	{
		if(started)
		{
			throw new IllegalStateException("Cannot add tasks after start of test");
		}
		int thisIndex = index++;
		work.add(new TaskTime(thisIndex, task, time));
		return thisIndex;
	}

	/**
	 * Starts test thread.
	 * @throws IllegalStateException If already started
	 */
	public synchronized void start() throws IllegalStateException
	{
		if(started)
		{
			throw new IllegalStateException("Test already started");
		}

		// Start thread
		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				thread();
			}
		}, "Work scheduler").start();
	}

	private void thread()
	{
		try
		{
			long startTime = System.currentTimeMillis();
			int percent = 0;
			int endTime = work.last().getTime() + ASSUMED_TASK_LENGTH;
			while(true)
			{
				TaskTime next;
				synchronized(this)
				{
					if(work.isEmpty())
					{
						handler.workFinished();
						return;
					}
					next = work.first();
					work.remove(next);
					while(true)
					{
						int time = (int)(System.currentTimeMillis() - startTime);
						int newPercent = Math.min((time * 100) / endTime, 100);
						if(newPercent != percent)
						{
							percent = newPercent;
							handler.percentComplete(percent);
						}

						if(time >= next.getTime() || stop)
						{
							break;
						}
						wait(next.getTime() - time);
					}
					if(stop)
					{
						return;
					}
					handler.runTask(next.getIndex(), next.getTask());
				}
			}
		}
		catch(InterruptedException e)
		{
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
	 * Stops thread (if running) and waits for it to complete.
	 */
	public synchronized void stop()
	{
		if(!started)
		{
			// Ignore error
			return;
		}

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

}
