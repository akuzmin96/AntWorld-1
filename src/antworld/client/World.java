package antworld.client;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Created by Anton on 12/1/2016.
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
  
  private static int getMapHeight(int rgb)
  {
    int g = (rgb & 0x0000FF00) >> 8;
    return Math.max(0, g - 48);
  }
  
  public Pixel[][] getWorld()
  {
    return world;
  }
}
