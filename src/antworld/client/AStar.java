package antworld.client;

/**
 * Created by Anton on 11/20/2016.
 */
public class AStar
{
  private int xPos, yPos, destinationX, destinationY;
  
  public AStar(int xPos, int yPos, int destinationX, int destinatonY)
  {
    this.xPos = xPos;
    this.yPos = yPos;
    this.destinationX = destinationX;
    this.destinationY = destinatonY;
  }
  
  public void calcPath()
  {
    
  }
  
  private int heuristic()
  {
    return Math.abs(xPos - destinationX) + Math.abs(yPos - destinationY);
  }
}
