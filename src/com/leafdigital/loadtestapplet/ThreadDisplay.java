package com.leafdigital.loadtestapplet;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;

import javax.swing.*;

/**
 * Swing component that displays thread status.
 */
public class ThreadDisplay extends JComponent
{
	private final static int BAR_WIDTH = 5;
	private final static int MIN_BAR_HEIGHT = 50;
	private final static int MARGIN = 4;
	private final static int MIN_TEXT_GAP = 4;

	private final static String LABEL_LAST = "Last:";
	private final static String LABEL_THIS = "This:";
	private final static String LABEL_MS = "ms";

	private final static Color LAST_COLOR = LoadTestApplet.DARK_COLOR;
	private final static Color THIS_COLOR = Color.BLACK;
	private final static Color BACKGROUND_COLOR = LoadTestApplet.LIGHT_COLOR;

	private ThreadHandler handler;
	private int scale;

	private JLabel lastLabel, thisLabel;
	private int thisTime, lastTime, beforeTime;

	private Font bgLetter;
	private int bgLetterX = -1, bgLetterY;
	private Color bgLetterColor;

	/**
	 * @param handler Handler
	 * @param scale Scale (max milliseconds to display)
	 */
	public ThreadDisplay(ThreadHandler handler, int scale)
	{
		this.handler = handler;
		this.scale = scale;

		setOpaque(true);
		setBackground(BACKGROUND_COLOR);
		setLayout(new BorderLayout());
		JPanel right = new JPanel(new BorderLayout());
		right.setOpaque(false);
		add(right, BorderLayout.EAST);

		lastLabel = new JLabel(LABEL_LAST + " 99999ms");
		lastLabel.setForeground(LAST_COLOR);
		lastLabel.setOpaque(false);
		right.add(lastLabel, BorderLayout.NORTH);
		thisLabel = new JLabel();
		thisLabel.setForeground(THIS_COLOR);
		thisLabel.setOpaque(false);
		right.add(thisLabel, BorderLayout.SOUTH);
		Font font = lastLabel.getFont();
		font = font.deriveFont(font.getSize2D() * 0.75f);
		lastLabel.setFont(font);
		thisLabel.setFont(font);

		Dimension textSize = lastLabel.getPreferredSize();
		right.setPreferredSize((Dimension)right.getPreferredSize().clone());
		lastLabel.setText("");

		setPreferredSize(new Dimension(
			BAR_WIDTH * 3 + MARGIN + textSize.width,
			Math.max(
				textSize.height * 2 + MIN_TEXT_GAP,
				MIN_BAR_HEIGHT
				)));

		bgLetter = lastLabel.getFont().deriveFont(Font.BOLD,
			getPreferredSize().height * 1.4f);
		bgLetterColor = new Color(LAST_COLOR.getRed(), LAST_COLOR.getGreen(),
			LAST_COLOR.getBlue(), 40);
	}

	/**
	 * Updates and repaints this display.
	 */
	public void update()
	{
		SwingUtilities.invokeLater(new Runnable()
		{
			@Override
			public void run()
			{
				synchronized(handler)
				{
					thisTime = handler.getThisTime();
					lastTime = handler.getLastTime();
					beforeTime = handler.getBeforeTime();
				}

				// Set up text labels
				if(thisTime == 0)
				{
					thisLabel.setText("");
				}
				else
				{
					thisLabel.setText(LABEL_THIS + " " + thisTime + LABEL_MS);
				}

				if(lastTime == 0)
				{
					lastLabel.setText("");
				}
				else
				{
					lastLabel.setText(LABEL_LAST + " " + lastTime + LABEL_MS);
				}

				// Repaint for bars (this will probably happen anyhow but)
				repaint();
			}
		});
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		g.setColor(BACKGROUND_COLOR);
		g.fillRect(0, 0, getWidth(), getHeight());

		// Paint the background letter
		String number = "" + handler.getIndex();
		if(bgLetterX == -1)
		{
			FontRenderContext frc = ((Graphics2D)g).getFontRenderContext();
			Rectangle2D stringBounds = bgLetter.getStringBounds(number, frc);
			int width = (int)(stringBounds.getWidth() + 0.5);
			bgLetterX = (getWidth() - width) / 2;
			bgLetterY = getHeight();// + (getHeight() - height) / 2;
		}
		((Graphics2D)g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
			RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(bgLetter);
		g.setColor(bgLetterColor);
		g.drawString(number, bgLetterX, bgLetterY);

		// Paint the three bars
		if(beforeTime != 0)
		{
			paintBar(g, 0, LAST_COLOR, beforeTime);
		}
		if(lastTime != 0)
		{
			paintBar(g, BAR_WIDTH, LAST_COLOR, lastTime);
		}
		if(thisTime != 0)
		{
			paintBar(g, 2 * BAR_WIDTH, Color.BLACK, thisTime);
		}
	}

	private void paintBar(Graphics g, int x, Color color, int time)
	{
		g.setColor(color);

		int max = getHeight();

		int height = (time * max) / scale;
		int overrun = -1;
		if(height > max)
		{
			height = max;
			overrun = height % max;
		}

		g.fillRect(x, max-height, BAR_WIDTH, max);
		if(overrun != -1)
		{
			g.setColor(Color.WHITE);
			g.fillRect(x, max-overrun, BAR_WIDTH, 1);
		}
	}
}
