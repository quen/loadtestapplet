package com.leafdigital.loadtestapplet;

import java.util.*;

/**
 * Tracking thread periodically monitors status of all handling threads.
 */
public class ThreadTracker
{
	private boolean started, stop, stopped;
	private Set<ThreadHandler> handlers = new HashSet<ThreadHandler>();
	private Set<ThreadHandler> removeHandlers = new HashSet<ThreadHandler>();

	/**
	 * Starts tracking thread.
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
		}, "Tracking thread").start();
	}

	/**
	 * Stops tracking thread.
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

	private void thread()
	{
		try
		{
			while(true)
			{
				// Wait for action
				synchronized(this)
				{
					while(!stop && handlers.isEmpty())
					{
						wait();
					}
					if(stop)
					{
						return;
					}
				}

				// Now that we have handlers, enter timing loop where we tick every
				// 25ms
				while(true)
				{
					ThreadHandler[] handlerArray;
					synchronized(this)
					{
						wait(25);
						if(stop)
						{
							return;
						}
						handlerArray = handlers.toArray(new ThreadHandler[handlers.size()]);
						// Remove things from array if removed, provided they were there
						// once
						for(ThreadHandler handler : handlerArray)
						{
							if(removeHandlers.contains(handler))
							{
								handlers.remove(handler);
							}
						}
						removeHandlers.clear();
					}

					for(ThreadHandler handler : handlerArray)
					{
						handler.tick();
					}

					if(handlerArray.length == 0)
					{
						break;
					}
				}
			}
		}
		catch(InterruptedException e)
		{
		}
		finally
		{
			stopped = true;
		}
	}

	/**
	 * Called by handler when it's doing something and needs to be ticked.
	 * @param handler Active handler
	 */
	public synchronized void startActivity(ThreadHandler handler)
	{
		handlers.add(handler);
		if(handlers.size() == 1)
		{
			notifyAll();
		}
	}

	/**
	 * Called by handler when it finishes doing something and can stop being
	 * ticked (after next tick).
	 * @param handler Inactive handler
	 */
	public synchronized void stopActivity(ThreadHandler handler)
	{
		removeHandlers.add(handler);
	}
}
