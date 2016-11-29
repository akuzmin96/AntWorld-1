package antworld.client;

/**
 * Created by Anton on 11/20/2016.
 */
public class Pixel
{
  private int x, y;
  private int[] neighbors;
  
  public Pixel(int x, int y)
  {
    this.x = x;
    this.y = y;
  }
  
  public int[] getNeighbors()
  {
    
    return neighbors;
  }
}
