package antworld.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.PriorityQueue;

/**
 * This class is designed to calculate the a star path for a specific ant. It is mainly used for ants
 * that have food and or ants that are healing to reduce computation.
 */
public class AStarPath {

  private Pixel[][] map;

  /**
   * Creates the A star object.
   * @param map
   */
  protected AStarPath(Pixel[][] map)
  {
    this.map = map;
  }

  /**
   * Will find and return the shortest path from pixel start to finish using the input map.
   * @param start starting pixel
   * @param end ending pixel
   * @return list of pixel represeting the path to follow.
   */
  protected List<Pixel> findAndReturnPath(Pixel start, Pixel end)
  {
    Pixel lastVisitedNode = null;
    Comparator<Pixel> pixelComparator = new Comparator<Pixel>() {
      @Override
      public int compare(Pixel p1, Pixel p2) {
        return p1.getPriority() - p2.getPriority();
      }
    };
    PriorityQueue<Pixel> frontier = new PriorityQueue<>(1, pixelComparator);
    frontier.add(start);
    LinkedHashMap<Pixel, Pixel> came_From = new LinkedHashMap<>();
    LinkedHashMap<Pixel, Integer> cost_So_Far = new LinkedHashMap<>();
    came_From.put(start, null);
    cost_So_Far.put(start, 0);

    while (!frontier.isEmpty())
    {
      Pixel current = frontier.poll();
      if (current.getX() == end.getX() && current.getY() == end.getY())
      {
        lastVisitedNode = current;
        break;
      }
      for(int i = current.getX() - 1; i <= current.getX() + 1; i++)
      {
        for(int j = current.getY() - 1; j <= current.getY() + 1; j++)
        {
          Pixel next = map[i][j];
          if(i != j && next.getType() != 'W')
          {
            int new_cost = cost_So_Far.get(current) + next.getHeight();
            if (!cost_So_Far.containsKey(next) || new_cost < cost_So_Far.get(next))
            {
              cost_So_Far.put(next, new_cost);
              int priority = new_cost + manhattanDistance(next.getX(), next.getY(), end.getX(), end.getY());
              next.setPriority(priority);
              frontier.add(next);
              came_From.put(next, current);
            }
          }
        }
      }
    }
    if (lastVisitedNode == null)
    {
      System.out.println("NO POSSIBLE PATH: " + start.getX() + " - " + start.getY());
      return null;
    }

    List<Pixel> path = new ArrayList<>();
    Pixel currentToReturn = end;
    while (currentToReturn != null)
    {
      path.add(0, currentToReturn);
      currentToReturn = came_From.get(currentToReturn);
    }

    return path;

}

  /**
   * Finds the manhattan distance from the starting x and y to ending xx and yy
   * @param x start x
   * @param y start y
   * @param xx end x
   * @param yy end y
   * @return distance
   */
  private int manhattanDistance(int x, int y, int xx, int yy)
  {
    return Math.abs(x - xx) + Math.abs(y - yy);
  }

}
