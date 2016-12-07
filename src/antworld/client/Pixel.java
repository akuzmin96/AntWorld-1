package antworld.client;

/**
 * Created by Anton on 11/20/2016.
 */
public class Pixel
{
  private int x, y, height;
  private char type;
  
  public Pixel(int x, int y, char type, int height)
  {
    this.x = x;
    this.y = y;
    this.type = type;
    this.height = height;
  }
  
  public int getX()
  {
    return x;
  }
  
  public int getY()
  {
    return y;
  }
  
  public char getType()
  {
    return type;
  }
  
  public int getHeight()
  {
    return height;
  }
}
