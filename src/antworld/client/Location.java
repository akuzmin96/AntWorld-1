package antworld.client;

/**
 * This class is used to store the location of the ant when it enters and exits the nest
 *
 * @author Joshua Donckels
 */
public class Location
{
  private int x;
  private int y;

  public Location(int x, int y)
  {
    this.x = x;
    this.y = y;
  }

  /**
   * Get the X position of the ant
   * @return x position
   */
  public int getX() {return x;}

  /**
   * Get the Y position of the ant
   * @return y position
   */
  public int getY() {return y;}
}
