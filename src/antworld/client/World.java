package antworld.client;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * This class reads in the map and creates a 2-D array representation of it.
 *
 * @author Anton
 */
public class World
{
  private BufferedImage map;
  private Pixel[][] world;
  private int width, height;
  
  public World()
  {
    try
    {
      map = ImageIO.read(getClass().getResourceAsStream("Small.png"));
      width = map.getWidth();
      height = map.getHeight();
    }
    catch (IOException e)
    {
      e.printStackTrace();
    }
    readMap(map);
  }
  
  /**
   * Read the map using a buffered image and assign the types for each pixel
   * @param map .png file to read
   */
  private void readMap(BufferedImage map)
  {
    world = new Pixel[width][height];
    
    for(int x = 0; x < width; x++)
    {
      for(int y = 0; y < height; y++)
      {
        int rgb = (map.getRGB(x, y) & 0x00FFFFFF);
        int height = 0;
        char type;
        
        if(rgb == 0x0)
        {
          type = 'N';
        }
        else if (rgb == 0xF0E68C)
        {
          type = 'N';
        }
        else if (rgb == 0x1E90FF)
        {
          type = 'W';
        }
        else
        {
          type = 'G';
          height = getMapHeight(rgb);
        }
        world[x][y] = new Pixel(x, y, type, height);
      }
    }
  }
  
  /**
   * Get the height of the pixel
   * @param rgb color index
   * @return height
   */
  private static int getMapHeight(int rgb)
  {
    int g = (rgb & 0x0000FF00) >> 8;
    return Math.max(0, g - 48);
  }
  
  /**
   * Get the 2-d array of the map
   * @return map
   */
  public Pixel[][] getWorld()
  {
    return world;
  }
}
