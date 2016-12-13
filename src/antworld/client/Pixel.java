package antworld.client;

/**
 * This class represents the different pixels on the map as characters
 *
 * @author Anton Kuzmin
 */
public class Pixel
{
  private int x, y, height;
  private char type;
  private int priority = 0;
  
  public Pixel(int x, int y, char type, int height)
  {
    this.x = x;
    this.y = y;
    this.type = type;
    this.height = height;
  }
  
  /**
   * Get the X position of the pixel
   * @return x position
   */
  public int getX()
  {
    return x;
  }
  
  /**
   * Get the Y position of the pixel
   * @return y position
   */
  public int getY()
  {
    return y;
  }
  
  /**
   * Get the character type of the pixel:
   * W = water
   * G = grass
   * N = nest
   * @return type of the pixel
   */
  public char getType()
  {
    return type;
  }
  
  /**
   * Get the height of grass the pixel
   * @return height of the pixel
   */
  public int getHeight()
  {
    return height;
  }

  /**
   * Sets the priority of the pixel for a star
   * @param priority
   */
  public void setPriority(int priority) { this.priority = priority; }

  /**
   * Gets the priority of the pixel for a star
   * @return priority
   */
  public int getPriority() { return priority; }
}
