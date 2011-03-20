package edu.spaced.edit.editor;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;

import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.vecmath.Vector2f;

import com.badlogic.gdx.math.Vector2;

import edu.spaced.simulation.Level;

public class Viewport extends JPanel {
	private ViewportDelegate delegate;
	
	// Editor variables
	private float snapDistance = 10;
	private float scrollSpeed = 1;
	private float zoom = 32;
	/**
	 * Since 1 "unit" is the size of a spaceship, lets make it so that the
	 * default zoom has a 32pixel ship size
	 * 
	 * Note: Measured from center of viewport!
	 */
	private Vector2 levelPosition = new Vector2(0,0); 
	
	private Level currentLevel;
	private Font font;
	
	// Drawing variables
	private boolean isDrawing;
	private boolean isSnapped;
	private ArrayList<Vector2> drawingPoints;
	private Point mouseLocation;
	private Vector2 levelCursor;
	
	public Viewport(Level level) {
		currentLevel = level;
		isDrawing = false;
		isSnapped = false;
		drawingPoints = null;
		mouseLocation = null;
		levelCursor = null;
		
		setLayout(new BorderLayout());
		setBackground(Color.BLACK);
		setDoubleBuffered(true);
		enableEvents( AWTEvent.KEY_EVENT_MASK |
					  AWTEvent.MOUSE_EVENT_MASK |
					  AWTEvent.MOUSE_MOTION_EVENT_MASK |
					  AWTEvent.MOUSE_WHEEL_EVENT_MASK );
		
		font = new Font("SansSerif", Font.PLAIN, 10);
		
		requestFocus();
	}
	
	public void setDelegate(ViewportDelegate delegate) {
		this.delegate = delegate;
	}

	public ViewportDelegate getDelegate() {
		return delegate;
	}

	@Override
	protected void paintComponent(Graphics g) {
		// Clear out background
		super.paintComponent(g);

		// Create an antialiased Graphics2D context
		Graphics2D g2d = (Graphics2D)g;
		RenderingHints renderHints = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		renderHints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2d.setRenderingHints(renderHints);

		// Draw the origin
		Vector2 origin = new Vector2(0,0);
		Vector2 origin_x1 = new Vector2(origin);
		Vector2 origin_x2 = new Vector2(origin);
		Vector2 origin_y1 = new Vector2(origin);
		Vector2 origin_y2 = new Vector2(origin);
		origin_x1.add(new Vector2( 0.5f, 0.0f));
		origin_x2.add(new Vector2(-0.5f, 0.0f));
		origin_y1.add(new Vector2( 0.0f, 0.5f));
		origin_y2.add(new Vector2( 0.0f,-0.5f));
		
		g2d.setColor(Color.GRAY);
		Line2D line = new Line2D.Float(levelToView(origin_x1), levelToView(origin_x2));
		g2d.draw(line);
		line = new Line2D.Float(levelToView(origin_y1), levelToView(origin_y2));
		g2d.draw(line);
		
		// Render the level
		
		// Render any line we are drawing
		g2d.setColor(Color.LIGHT_GRAY);
		if (isDrawing) {
			for (int i = 0; i < drawingPoints.size() - 1; i++) {
				line = new Line2D.Float(levelToView(drawingPoints.get(i)), levelToView(drawingPoints.get(i+1)));
				g2d.draw(line);
			}
			// Draw the line from the last point to the current mouse position
			line = new Line2D.Float(levelToView(drawingPoints.get(drawingPoints.size()-1)), levelToView(levelCursor));
			g2d.draw(line);
		}
		
		// Render the "snapped" highlight
		if (isSnapped) {
			Ellipse2D highlight = new Ellipse2D.Double(levelToView(levelCursor).getX() - snapDistance / 2, levelToView(levelCursor).getY() - snapDistance / 2, snapDistance, snapDistance);
			g2d.setColor(Color.YELLOW);
			g2d.draw(highlight);
		}
		
		// Give some coordinates in the upper right
		if (levelCursor != null) {
			FontMetrics fm = g2d.getFontMetrics(font);
			String coordString = new String("(" + levelCursor.x + ", " + levelCursor.y + ")");
			int width = fm.stringWidth(coordString);
			g2d.setFont(font);
			g2d.setColor(Color.WHITE);
			g2d.drawString(coordString, 0 ,0 );//getWidth() - width - 10, getHeight() + 10);
		}
	}
	
	/////////////////////////
	// Coordinate transform
	public Vector2 viewToLevel(Point viewCoords) {
		Vector2 result = new Vector2(viewCoords.x, viewCoords.y);
		result.sub(new Vector2(getWidth() / 2, getHeight() / 2));
		result.mul(1/zoom);
		result.add(levelPosition);
		return result;		
	}
	
	public Point2D levelToView(Vector2 worldCoords) {
		Vector2 result = new Vector2(worldCoords);
		result.sub(levelPosition);
		result.mul(zoom);
		result.add(new Vector2(getWidth() / 2, getHeight() / 2));
		
		return new Point2D.Float(result.x, result.y);
	}

	///////////////////
	// Drawing points
	
	protected void addDrawingPoint(Vector2 point) {
		if (drawingPoints == null)
			drawingPoints = new ArrayList<Vector2>();
		
		drawingPoints.add(point);
		delegate.updateStatus("Added point at (" + point.x + ", " + point.y + ")");
		
		// If this point was snapped to something, commit the drawing. The
		// user can always begin drawing again snapping to the same point.
		if (isSnapped)
			commitDrawing();
		
		repaint();
	}
	
	protected void commitDrawing() {
		isDrawing = false;
		
		if (drawingPoints == null)
			return;
		
		delegate.addWalls(drawingPoints.toArray(new Vector2[drawingPoints.size()]));
		delegate.updateStatus("Added " + (drawingPoints.size() - 1) + " new walls.");
		
		drawingPoints = null;
		
		updateCursorSnap();
		
		repaint();
	}
	
	private void cancelDrawing() {
		isDrawing = false;
		drawingPoints = null;
		
		delegate.updateStatus("Canceled drawing.");
		
		repaint();
	}

	///////////////////
	// Input handling
	
	protected void updateCursorSnap() {
		Vector2 nearestPoint = currentLevel.findNearestPointWithin(levelCursor.x, levelCursor.y, snapDistance / zoom);
		// If the current segment we're drawing is within snap range and if
		// there are enough segments to make a triangle, then always snap.
		if (isDrawing && drawingPoints.size() > 2
			&& levelToView(levelCursor).distance(levelToView(drawingPoints.get(0))) < snapDistance) {
			nearestPoint = drawingPoints.get(0);
		}
		if (nearestPoint != null) {
			isSnapped = true;
			levelCursor = nearestPoint;
		} else {
			isSnapped = false;
		}
	}
	
	@Override
	protected void processKeyEvent(KeyEvent e) {
		if (e.getID() == KeyEvent.KEY_PRESSED) {
			switch (e.getKeyCode()) {
			case KeyEvent.VK_KP_UP:
			case KeyEvent.VK_UP:
				levelPosition.add(new Vector2(0, -scrollSpeed / zoom));
				repaint();
				break;
			case KeyEvent.VK_KP_DOWN:
			case KeyEvent.VK_DOWN:
				levelPosition.add(new Vector2(0, scrollSpeed / zoom));
				repaint();
				break;
			case KeyEvent.VK_KP_LEFT:
			case KeyEvent.VK_LEFT:
				levelPosition.add(new Vector2(-scrollSpeed / zoom, 0));
				repaint();
				break;
			case KeyEvent.VK_KP_RIGHT:
			case KeyEvent.VK_RIGHT:
				levelPosition.add(new Vector2(scrollSpeed / zoom, 0));
				repaint();
				break;
			case KeyEvent.VK_SPACE:
				if (isDrawing && levelCursor != null) {
					addDrawingPoint(levelCursor);
				}
				break;
			case KeyEvent.VK_ESCAPE:
				if (isDrawing) {
					cancelDrawing();
				}
				break;
			case KeyEvent.VK_BACK_SPACE:
				if (isDrawing) {
					drawingPoints.remove(drawingPoints.size() - 1);
					if (drawingPoints.size() == 0) {
						cancelDrawing();
					} else {
						repaint();
					}
				}
				break;
			case KeyEvent.VK_ENTER:
				if (isDrawing) {
					commitDrawing();
				}
			}
		}

		updateCursorSnap();

		super.processKeyEvent(e);
	}

	@Override
	protected void processMouseEvent(MouseEvent e) {
		requestFocus();
		mouseLocation = e.getPoint();
		levelCursor = viewToLevel(mouseLocation);
		updateCursorSnap();
		
		if (e.isPopupTrigger()) {
			JMenuItem item = new JMenuItem("Not implemented yet...");
			JPopupMenu popup = new JPopupMenu();
			popup.add(item);
			popup.show(e.getComponent(), e.getX(), e.getY());
		} else if (e.getID() == MouseEvent.MOUSE_PRESSED) {
			isDrawing = true;
			addDrawingPoint(levelCursor);
		} else if (e.getID() == MouseEvent.MOUSE_ENTERED) {
			this.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
		} else if (e.getID() == MouseEvent.MOUSE_EXITED) {
			this.setCursor(Cursor.getDefaultCursor());
			levelCursor = null;
		}

		e.consume();
	}	

	@Override
	protected void processMouseMotionEvent(MouseEvent e) {
		if (isDrawing) {
 			// Check if we're selecting a nearby point
			mouseLocation = e.getPoint();
			levelCursor = viewToLevel(mouseLocation);			
		}

		updateCursorSnap();
		
		repaint();
		
		e.consume();
	}

	@Override
	protected void processMouseWheelEvent(MouseWheelEvent e) {
		zoom -= e.getWheelRotation();
		if (zoom < 1)
			zoom = 1;
		
		updateCursorSnap();
		
		repaint();
		
		e.consume();
	}
	
	
}
