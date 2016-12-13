package antworld.client;

import antworld.common.AntData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.PriorityQueue;


public class AStarPath {

  private Pixel[][] map;

  protected AStarPath(Pixel[][] map)
  {
    this.map = map;
  }

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
    int mDistance = manhattanDistance(start.getX(), start.getY(), end.getX(), end.getY());

    while (!frontier.isEmpty())
    {
      Pixel current = frontier.poll();
      if (current.getX() == end.getX() && current.getY() == end.getY())
      {
        lastVisitedNode = current;
        break;
      }
      for(int i = current.getX() - 1; i < current.getX() + 1; i++)
      {
        for(int j = current.getY() - 1; i < current.getY() + 1; i++)
        {
          if(i != j && i > 0 && j > 0)
          {
            Pixel next = map[i][j];
            if(next.getType() != 'W')
            {
              int new_cost = cost_So_Far.get(current) + next.getHeight();
              if (!cost_So_Far.containsKey(next) || new_cost < cost_So_Far.get(next))
              {
                cost_So_Far.put(next, new_cost);
                int priority = new_cost + mDistance;
                next.setPriority(priority);
                frontier.add(next);
                came_From.put(next, current);
              }
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


  private int manhattanDistance(int x, int y, int xx, int yy)
  {
    return Math.abs(x - xx) + Math.abs(y - yy);
  }

}
