package antworld.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;

import antworld.common.*;
import antworld.common.AntAction.AntActionType;

import static java.lang.Integer.toBinaryString;
import static java.lang.Math.*;
import static oracle.jrockit.jfr.events.Bits.intValue;

/**
 * The brain of our ant AI.
 * Exploring in pulses: Ants go out, rotate, and the come back (ensures that they cover everything around the base)
 * Attacking: Ants attack if they outnumber an enemy ant, if the enemy ant is carrying food, or if the enemy attacked
 * Water: There are 10 ants that gather water until the water supply is 5000 units
 * Food: As soon as an ant sees food, other ants come to help gather all of it and return to the base
 * Healing: Ant goes back to base if it is hurt
 * Getting stuck: Ants get unstuck if they are clumped or if they hit water
 *
 * @author Anton Kuzmin and Joshua Donckels
 */
public class JoshAntonAntAI
{
  private static final boolean DEBUG = false;
  private static final TeamNameEnum myTeam = TeamNameEnum.Josh_Anton;
  private static final long password = 962740848319L; //Each team has been assigned a random password.
  private ObjectInputStream inputStream = null;
  private ObjectOutputStream outputStream = null;
  private boolean isConnected = false;
  private NestNameEnum myNestName = null;
  private int centerX, centerY;

  private World world = new World();
  private Pixel[][] map = world.getWorld();
  private AStarPath AStar = new AStarPath(map);
  private static final int DIR_BIT_N  = 1;
  private static final int DIR_BIT_NE = 2;
  private static final int DIR_BIT_E  = 4;
  private static final int DIR_BIT_SE = 8;
  private static final int DIR_BIT_S  = 16;
  private static final int DIR_BIT_SW = 32;
  private static final int DIR_BIT_W  = 64;
  private static final int DIR_BIT_NW = 128;
  private int waterX = -1;
  private int waterY = -1;
  private double exitAngle = 0;
  private boolean firstExit = true;
  private int explorationDistance = 200;
  private int lastExplorationDistance = 0;
  private int explorationTwistTick = 60;
  private int lastExplorationTwistTick = 0;
  private boolean setPreviousTick = true;
  private int previousTick = 0;
  private boolean isTwisting = false;
  private int exitCountForInitial = 0;
  private int antsExploring = 0;
  private Queue<Location> exitQueue = new LinkedList<>();
  private ArrayList<AntData> waterAnts = new ArrayList<>();
  private ArrayList<AntHistory> antHistories = new ArrayList<>();
  private ArrayList<Integer> historyForExploring = new ArrayList<>();
  private LinkedHashMap<Integer, List<Pixel>> listOfPaths = new LinkedHashMap<>();
  
  private Socket clientSocket;
  private static Random random = Constants.random;

  public JoshAntonAntAI(String host, int portNumber)
  {
    System.out.println("Starting JoshAntonAntAI: " + System.currentTimeMillis());
    isConnected = false;
    while (!isConnected)
    {
      isConnected = openConnection(host, portNumber);
      if (!isConnected) try { Thread.sleep(2500); } catch (InterruptedException e1) {}
    }
    CommData data = obtainNest();
    mainGameLoop(data);
    closeAll();
  }

  
  private boolean openConnection(String host, int portNumber)
  {
    try
    {
      clientSocket = new Socket(host, portNumber);
    }
    catch (UnknownHostException e)
    {
      System.err.println("JoshAntonAntAI Error: Unknown Host " + host);
      e.printStackTrace();
      return false;
    }
    catch (IOException e)
    {
      System.err.println("JoshAntonAntAI Error: Could not open connection to " + host + " on port " + portNumber);
      e.printStackTrace();
      return false;
    }

    try
    {
      outputStream = new ObjectOutputStream(clientSocket.getOutputStream());
      inputStream = new ObjectInputStream(clientSocket.getInputStream());

    }
    catch (IOException e)
    {
      System.err.println("JoshAntonAntAI Error: Could not open i/o streams");
      e.printStackTrace();
      return false;
    }
    return true;
  }

  public void closeAll()
  {
    System.out.println("JoshAntonAntAI.closeAll()");
    {
      try
      {
        if (outputStream != null) outputStream.close();
        if (inputStream != null) inputStream.close();
        clientSocket.close();
      }
      catch (IOException e)
      {
        System.err.println("JoshAntonAntAI Error: Could not close");
        e.printStackTrace();
      }
    }
  }

  /**
   * This method is called ONCE after the socket has been opened.
   * The server assigns a nest to this client with an initial ant population.
   * @return a reusable CommData structure populated by the server.
   */
  public CommData obtainNest()
  {
      CommData data = new CommData(myTeam);
      data.password = password;

      if( sendCommData(data) )
      {
        try
        {
          if (DEBUG) System.out.println("JoshAntonAntAI: listening to socket....");
          data = (CommData) inputStream.readObject();
          if (DEBUG) System.out.println("JoshAntonAntAI: received <<<<<<<<<"+inputStream.available()+"<...\n" + data);
          
          if (data.errorMsg != null)
          {
            System.err.println("JoshAntonAntAI***ERROR***: " + data.errorMsg);
            System.exit(0);
          }
        }
        catch (IOException e)
        {
          System.err.println("JoshAntonAntAI***ERROR***: client read failed");
          e.printStackTrace();
          System.exit(0);
        }
        catch (ClassNotFoundException e)
        {
          System.err.println("JoshAntonAntAI***ERROR***: client sent incorrect common format");
        }
      }
    if (data.myTeam != myTeam)
    {
      System.err.println("JoshAntonAntAI***ERROR***: Server returned wrong team name: "+data.myTeam);
      System.exit(0);
    }
    if (data.myNest == null)
    {
      System.err.println("JoshAntonAntAI***ERROR***: Server returned NULL nest");
      System.exit(0);
    }

    myNestName = data.myNest;
    centerX = data.nestData[myNestName.ordinal()].centerX;
    centerY = data.nestData[myNestName.ordinal()].centerY;
    System.out.println("JoshAntonAntAI: ==== Nest Assigned ===>: " + myNestName);
    return data;
  }
    
  public void mainGameLoop(CommData data)
  {
    //Set up the history objects for each ant
    for(AntData ant : data.myAntList)
    {
      antHistories.add(new AntHistory(ant.id));
    }
    
    while (true)
    { 
      try
      {
        if (DEBUG) System.out.println("JoshAntonAntAI: chooseActions: " + myNestName);

        chooseActionsOfAllAnts(data);  

        CommData sendData = data.packageForSendToServer();
        
        if (DEBUG) System.out.println("JoshAntonAntAI: Sending>>>>>>>: " + sendData);
        outputStream.writeObject(sendData);
        outputStream.flush();
        outputStream.reset();
        
        if (DEBUG) System.out.println("JoshAntonAntAI: listening to socket....");
        CommData receivedData = (CommData) inputStream.readObject();
        if (DEBUG) System.out.println("JoshAntonAntAI: received <<<<<<<<<"+inputStream.available()+"<...\n" + receivedData);
        data = receivedData;
        
        if ((myNestName == null) || (data.myTeam != myTeam))
        {
          System.err.println("JoshAntonAntAI: !!!!ERROR!!!! " + myNestName);
        }
      }
      catch (IOException e)
      {
        System.err.println("JoshAntonAntAI***ERROR***: client read failed");
        e.printStackTrace();
        System.exit(0);
      }
      catch (ClassNotFoundException e)
      {
        System.err.println("ServerToClientConnection***ERROR***: client sent incorrect common format");
        e.printStackTrace();
        System.exit(0);
      }
    }
  }
  
  private boolean sendCommData(CommData data)
  {
    CommData sendData = data.packageForSendToServer();
    try
    {
      if (DEBUG) System.out.println("JoshAntonAntAI.sendCommData(" + sendData +")");
      outputStream.writeObject(sendData);
      outputStream.flush();
      outputStream.reset();
    }
    catch (IOException e)
    {
      System.err.println("JoshAntonAntAI***ERROR***: client read failed");
      e.printStackTrace();
      System.exit(0);
    }

    return true;
  }

  private void chooseActionsOfAllAnts(CommData data)
  {
    for (AntData ant : data.myAntList)
    {
      AntAction action = chooseAction(data, ant);
      ant.myAction = action;
    }
    
    //Spawn ants [Water, Nectar, Seeds, Meet] when there is enough food
    if(data.foodStockPile[3] > 20)
    {
      AntData attackAnt = new AntData(-1, AntType.ATTACK, myNestName, myTeam);
      data.myAntList.add(attackAnt);
    }
    if(data.foodStockPile[1] > 20)
    {
      AntData speedAnt = new AntData(-1, AntType.SPEED, myNestName, myTeam);
      data.myAntList.add(speedAnt);
    }
    if(data.foodStockPile[2] > 20)
    {
      AntData workerAnt = new AntData(-1, AntType.WORKER, myNestName, myTeam);
      data.myAntList.add(workerAnt);
    }

    antsExploring = 0;
  }
  
  /**
   * Get the Manhattan distance between two points
   * @param x position 1
   * @param y position 1
   * @param xx position 2
   * @param yy position 2
   * @return distance
   */
  private int manhattanDistance(int x, int y, int xx, int yy)
  {
    return Math.abs(x - xx) + Math.abs(y - yy);
  }
  
  /**
   * Exit the nest with the position being based on a circle for better exploration
   * @param ant current ant
   * @param action action of ant
   * @param data comm data
   * @return exit nest
   */
  private boolean exitNest(AntData ant, AntAction action, CommData data)
  {
    if (ant.underground == false) return false;
    if (ant.carryUnits > 0) return false;
    if (ant.health < ant.antType.getMaxHealth()) return false;

    action.type = AntActionType.EXIT_NEST;

    if (firstExit)
    {
      double exitIncrement = 6 * Math.PI/data.myAntList.size();

      if (exitCountForInitial >= data.myAntList.size()) firstExit = false;

      if (exitAngle < 2 * Math.PI)
      {
        action.x = centerX + intValue(15 * cos(exitAngle));
        action.y = centerY + intValue(15 * sin(exitAngle));
        exitAngle += exitIncrement;
      }
      else if (exitAngle < 4 * Math.PI)
      {
        action.x = centerX + intValue(10 * cos(exitAngle));
        action.y = centerY + intValue(10 * sin(exitAngle));
        exitAngle += exitIncrement;
      }
      else
      {
        action.x = centerX + intValue(5 * cos(exitAngle));
        action.y = centerY + intValue(5 * sin(exitAngle));
        exitAngle += exitIncrement;
      }
      exitCountForInitial++;
    }
    else
    {
      if (!exitQueue.isEmpty())
      {
        Location loc = exitQueue.remove();
        action.x = loc.getX();
        action.y = loc.getY();
      }
      else
      {
        action.x = centerX - (Constants.NEST_RADIUS - 1) + random.nextInt(2 * (Constants.NEST_RADIUS - 1));
        action.y = centerY - (Constants.NEST_RADIUS - 1) + random.nextInt(2 * (Constants.NEST_RADIUS - 1));
      }
    }
    return true;
  }
  
  /**
   * If ants are clumped up, make them go in different directions
   * @param ant current ant
   * @param action action of ant
   * @param data comm data
   * @return move in a random open direction
   */
  private boolean unStickAnts(AntData ant, AntAction action, CommData data)
  {
    int neighborCounter = 0;
    for (AntData neighborAnt : data.myAntList)
    {
      if (manhattanDistance(ant.gridX, ant.gridY, neighborAnt.gridX, neighborAnt.gridY) < 2 && data.gameTick > 200)
      {
        neighborCounter++;
      }
    }
    
    if (neighborCounter > 2)
    {
      int dirBits = getDirectionBitsOpen(ant);
      Direction dir = getRandomDirection(dirBits);
      action.type = AntActionType.MOVE;
      action.direction = dir;
      return true;
    }
    return false;
  }
  
  /**
   * Go toward a certain spot on the map
   * @param ant current ant
   * @param x position to go to
   * @param y position to go to
   * @param action action of ant
   * @return move to the x and y position
   */
  private boolean goToward(AntData ant, int x, int y, AntAction action)
  {
    int dirBits = getDirectionBitsOpen(ant);
    dirBits = getDirBitsToLocation(dirBits, ant.gridX, ant.gridY, x, y);
    Direction dir = getRandomDirection(dirBits);
    
    if (dir == null) return false;
    action.type = AntActionType.MOVE;
    action.direction = dir;
    return true;
  }
  
  /**
   * Get a random direction to move to
   * @param dirBits bits that are open
   * @return direction
   */
  private static Direction getRandomDirection(int dirBits)
  {
    Direction dir = Direction.getRandomDir();
    for (int i = 0; i < Direction.SIZE; i++)
    {
      int bit = 1 << dir.ordinal();
      if ((bit & dirBits) != 0) return dir;
      
      dir = Direction.getRightDir(dir);
    }
    return null;
  }
  
  /**
   * Get the directional bits that are open for the ant to move.
   * @param ant current ant
   * @return bits that are open
   */
  private int getDirectionBitsOpen(AntData ant)
  {
    if (DEBUG) System.out.println("  getDirectionBitsOpen()");
    int dirBits = 255;
    for (Direction dir : Direction.values())
    {
      int x = ant.gridX + dir.deltaX();
      int y = ant.gridY + dir.deltaY();
      int bit = 1 << dir.ordinal();
      Pixel neighborCell = map[x][y];
      
      if (neighborCell == null) dirBits = dirBits & bit;
      else if (neighborCell.getType() == 'W') dirBits -= bit;
      else if (neighborCell.getType() != 'G' && ant.gridX > centerX + 10 && ant.gridX < centerX - 10 &&
              ant.gridY > centerY + 10 && ant.gridY < centerY - 10)  dirBits -= bit;
    }
    return dirBits;
  }
  
  /**
   * Get the direction bits to a certain location
   * @param dirBits  direction bits that are open
   * @param x position 1
   * @param y position 1
   * @param xx position 2
   * @param yy position 2
   * @return bits to location
   */
  private static int getDirBitsToLocation(int dirBits, int x, int y, int xx, int yy)
  {
    double angle = atan2(yy - y,xx - x);
    angle = toDegrees(angle);
    angle += 180;

    if(angle > 22.5 && angle <= 67.5)
    {
      dirBits = dirBits & (DIR_BIT_NW);
    }
    else if(angle > 67.5 && angle <= 112.5)
    {
      dirBits = dirBits & (DIR_BIT_N);
    }
    else if(angle > 112.5 && angle <= 157.5)
    {
      dirBits = dirBits & (DIR_BIT_NE);
    }
    else if(angle > 157.5 && angle <= 202.5)
    {
      dirBits = dirBits & (DIR_BIT_E);
    }
    else if(angle > 202.5 && angle <= 247.5)
    {
      dirBits = dirBits & (DIR_BIT_SE);
    }
    else if(angle > 247.5 && angle <= 292.5)
    {
      dirBits = dirBits & (DIR_BIT_S);
    }
    else if(angle > 292.5 && angle <= 337.5)
    {
      dirBits = dirBits & (DIR_BIT_SW);
    }
    else
    {
      dirBits = dirBits & (DIR_BIT_W);
    }
    return dirBits;
  }
  
  /**
   * Go home if the ant needs to heal of drop off food, will use A star to calculate the path if the ant needs
   * to be healed or has food that is not water.
   * @param ant current ant
   * @param action action of ant
   * @param homeAction what to do, heal or drop off
   * @param data comm data
   * @return action for home
   */
  private boolean goHome(AntData ant, AntAction action, int homeAction, CommData data)
  {

    Pixel fromAStar;

    if((!listOfPaths.containsKey(ant.id) || listOfPaths.get(ant.id) == null) && ant.carryType != FoodType.WATER)
    {
      listOfPaths.put(ant.id, AStar.findAndReturnPath(map[ant.gridX][ant.gridY], map[centerX][centerY]));
    }

    if (homeAction == 1 && ant.underground)
    {
      action.type = AntActionType.HEAL;
      if (DEBUG) System.out.println("Healed");
      return true;
    }
    
    if (homeAction == 2 && ant.underground)
    {
      action.type = AntActionType.DROP;
      action.direction = Direction.NORTH;
      action.quantity = ant.carryUnits;
      
      //Print out nest food supply
      if (DEBUG)
      {
        for (int i = 0; i < data.foodStockPile.length; i++)
        {
          System.out.println(data.foodStockPile[i]);
        }
      }
      return true;
    }

    int distance = manhattanDistance(ant.gridX, ant.gridY, centerX, centerY);

    if (distance < 21)
    {
      exitQueue.add(new Location(ant.gridX, ant.gridY));
      if(ant.carryType != FoodType.WATER)
      {
        listOfPaths.remove(ant.id);
      }
      action.type = AntActionType.ENTER_NEST;
      if (DEBUG) System.out.println("Entered nest");
      return true;
    }

    if(ant.carryType != FoodType.WATER && listOfPaths.get(ant.id) != null)
    {
      int cX = ant.gridX;
      int cY = ant.gridY;
      fromAStar = listOfPaths.get(ant.id).get(0);
      int nX = fromAStar.getX();
      int nY = fromAStar.getY();

      if(cX == nX && cY == nY)
      {
        listOfPaths.get(ant.id).remove(0);
        fromAStar = listOfPaths.get(ant.id).get(0);
        nX = fromAStar.getX();
        nY = fromAStar.getY();
      }


      action.type = AntActionType.MOVE;
      if( cX == nX && cY - 1 == nY )
      {
        action.direction = Direction.NORTH;
      }
      else if( cX + 1 == nX && cY - 1 == nY )
      {
        action.direction = Direction.NORTHEAST;
      }
      else if( cX + 1 == nX && cY == nY )
      {
        action.direction = Direction.EAST;
      }
      else if( cX + 1 == nX && cY + 1 == nY )
      {
        action.direction = Direction.SOUTHEAST;
      }
      else if( cX == nX && cY + 1 == nY )
      {
        action.direction = Direction.SOUTH;
      }
      else if( cX - 1 == nX && cY + 1 == nY )
      {
        action.direction = Direction.SOUTHWEST;
      }
      else if( cX - 1 == nX && cY == nY )
      {
        action.direction = Direction.WEST;
      }
      else if( cX - 1 == nX && cY - 1 == nY )
      {
        action.direction = Direction.NORTHWEST;
      }
      else
      {
        listOfPaths.remove(ant.id);
        listOfPaths.put(ant.id, AStar.findAndReturnPath(map[ant.gridX][ant.gridY], map[centerX][centerY]));
      }

      return true;
    }

    return goToward(ant, centerX, centerY, action);
  }
  
  /**
   * Go home if the ant is carrying food or needs to heal
   * @param ant current ant
   * @param action action of ant
   * @param data comm data
   * @return a call to the goHome function with the proper action (heal or drop)
   */
  private boolean goHomeIfCarryingOrHurt(AntData ant, AntAction action, CommData data)
  {
    if (ant.health <= ant.antType.getMaxHealth()/1.8)
    {
      return goHome(ant, action, 1, data);
    }
    else if (ant.carryUnits >= 10 && ant.carryType != FoodType.WATER)
    {
      return goHome(ant, action, 2, data);
    }
    else if (ant.carryUnits >= ant.antType.getCarryCapacity() && ant.carryType == FoodType.WATER)
    {
      return goHome(ant, action, 2, data);
    }
    return false;
  }
  
  /**
   * Pick up any food that is adjacent to the ant
   * @param ant current ant
   * @param action action of ant
   * @param food food to be picked up
   * @return action to pick up as much food as possible
   */
  private boolean pickUpFoodAdjacent(AntData ant, AntAction action, FoodData food)
  {
    if (DEBUG) System.out.println("  pickUpFoodAdjactent()");
    
    for (Direction dir : Direction.values())
    {
      int x = ant.gridX + dir.deltaX();
      int y = ant.gridY + dir.deltaY();
      Pixel neighborCell = map[x][y];
      
      if (neighborCell == null) continue;
      
      if (neighborCell.getType() == 'W') continue;
      
      if (neighborCell.getX() == food.gridX && neighborCell.getY() == food.gridY)
      {
        action.type = AntActionType.PICKUP;
        action.direction = dir;
        if (food.getCount() < ant.antType.getCarryCapacity())
        {
          if (DEBUG) System.out.println("Picked up leftovers");
          action.quantity = food.getCount();
          return true;
        }
        else
        {
          if (DEBUG) System.out.println("Picked up everything");
          action.quantity = ant.antType.getCarryCapacity();
          return true;
        }
      }
    }
    return false;
  }
  
  /**
   * Go to a food source if it is visible to any ant
   * @param ant current ant
   * @param action action of ant
   * @param data comm data
   * @return go toward the food source or pick up the food if close enougb
   */
  private boolean goToFood(AntData ant, AntAction action, CommData data)
  {
    if (!data.foodSet.isEmpty())
    {
      for (FoodData food : data.foodSet)
      {
        int distance = manhattanDistance(ant.gridX, ant.gridY, food.gridX, food.gridY);
        if (distance < 2)
        {
          return pickUpFoodAdjacent(ant, action, food);
        }
        
        if (distance < 60)
        {
          return goToward(ant, food.gridX, food.gridY, action);
        }
      }
    }
    return false;
  }
  
  /**
   * Pick up water if the ant is close enough, the nest water stock is less that 5000,
   * and the ant is in the list of dedicated water collecting ants
   * @param ant current ant
   * @param action action of ant
   * @param data comm data
   * @return pick up the water
   */
  private boolean pickUpWater(AntData ant, AntAction action, CommData data)
  {
    if (DEBUG) System.out.println("  pickUpWater()");

    if (data.foodStockPile[0] > 5000) return false;

    int distance = manhattanDistance(ant.gridX, ant.gridY, waterX, waterY);
    
    if (distance < 45 && waterAnts.size() < 10)
    {
      waterAnts.add(ant);
      if (DEBUG) System.out.println("Adding ant to list for water");
    }
    
    if (!waterAnts.contains(ant) && !waterAnts.isEmpty()) return false;
    
    if ((waterX != -1 || waterY != -1) && distance > 2 && waterAnts.contains(ant))
    {
      return goToward(ant, waterX, waterY, action);
    }
    
    for (Direction dir : Direction.values())
    {
      int x = ant.gridX + dir.deltaX();
      int y = ant.gridY + dir.deltaY();
      Pixel neighborCell = map[x][y];
      
      if (neighborCell == null) continue;
      
      if (neighborCell.getType() == 'W')
      {
        if (waterX == -1 || waterY == -1)
        {
          waterX = neighborCell.getX();
          waterY = neighborCell.getY();
        }
        action.type = AntActionType.PICKUP;
        action.direction = dir;
        action.quantity = ant.antType.getCarryCapacity();
        return true;
      }
    }
    return false;
  }
  
  /**
   * Attack the adjacent enemy ant
   * @param ant current ant
   * @param action action of ant
   * @param enemyAnt ant to attack
   * @return attack
   */
  private boolean attackAdjacent(AntData ant, AntAction action, AntData enemyAnt)
  {
    if (DEBUG) System.out.println("  attackAdjacent()");
    if (DEBUG) System.out.println("Calling attack");
    for (Direction dir : Direction.values())
    {
      int x = ant.gridX + dir.deltaX();
      int y = ant.gridY + dir.deltaY();
      Pixel neighborCell = map[x][y];
      
      if (neighborCell == null) continue;
      
      if (neighborCell.getType() == 'W') continue;
      
      if (enemyAnt.teamName == TeamNameEnum.Josh_Anton) continue;
      
      if (neighborCell.getX() == enemyAnt.gridX && neighborCell.getY() == enemyAnt.gridY)
      {
        action.type = AntActionType.ATTACK;
        action.direction = dir;
        if (DEBUG) System.out.println("Attacking ant");
        return true;
      }
    }
    return false;
  }
  
  /**
   * Go to an enemy ant and attack it if it is carrying food other than water,
   * has attacked my ant, or if my ants outnumber it
   * @param ant current ant
   * @param action action of ant
   * @param data comm data
   * @return goToward enemy ant and attack it if in range
   */
  private boolean goToEnemyAnt(AntData ant, AntAction action, CommData data)
  {
    if (!data.enemyAntSet.isEmpty())
    {
      for (AntData enemyAnt : data.enemyAntSet)
      {
        int friendlyCounter = 0;
        int enemyCounter = 0;
        boolean outnumber = false;
        
        for (AntData friendlyAntNearby : data.myAntList)
        {
          if (manhattanDistance(friendlyAntNearby.gridX, friendlyAntNearby.gridY, enemyAnt.gridX, enemyAnt.gridY) < 35)
          {
            friendlyCounter++;
          }
        }
  
        for (AntData enemyAntNearby : data.enemyAntSet)
        {
          if (manhattanDistance(enemyAntNearby.gridX, enemyAntNearby.gridY, enemyAnt.gridX, enemyAnt.gridY) < 35)
          {
            enemyCounter++;
          }
        }
        
        if (friendlyCounter > enemyCounter)
        {
          outnumber = true;
          if (DEBUG) System.out.println("Outnumber");
        }
        
        if (friendlyCounter + 5 < enemyCounter)
        {
          return goToward(ant, centerX, centerY, action);
        }
        
        if ((enemyAnt.carryUnits > 0 && enemyAnt.carryType != FoodType.WATER) ||
                enemyAnt.myAction.type == AntActionType.ATTACK || outnumber)
        {
          int distance = manhattanDistance(ant.gridX, ant.gridY, enemyAnt.gridX, enemyAnt.gridY);
  
          if (distance < 2)
          {
            return attackAdjacent(ant, action, enemyAnt);
          }
  
          if (distance < 40)
          {
            for (AntHistory history : antHistories)
            {
              if (ant.id == history.getAntID())
              {
                if (history.getEnemyAnt() != null)
                {
                  history.setEnemyAnt(enemyAnt);
                  if (DEBUG) System.out.println(ant.id + " going to enemy ant " + history.getEnemyAnt().id);
                  return goToward(ant, history.getEnemyAnt().gridX, history.getEnemyAnt().gridY, action);
                }
        
                if (distance < 40 && history.getEnemyAnt() == null)
                {
                  if (DEBUG) System.out.println(ant.id + " setting new enemy ant " + enemyAnt.id);
                  history.setEnemyAnt(enemyAnt);
                }
              }
            }
          }
        }
      }
    }
    return false;
  }
  
  /**
   * If an ant encounters water get the next open location to the right of the ant ant go there
   * @param ant current ant
   * @param action action of ant
   * @return go to next open location
   */
  private boolean unStickOnWater(AntData ant, AntAction action)
  {
    if (!waterAnts.contains(ant))
    {
      for (int x = ant.gridX - 1; x < ant.gridX + 2; x++)
      {
        for (int y = ant.gridY - 1; y < ant.gridY + 2; y++)
        {
          if (map[x][y].getType() == 'W')
          {
            int dirBits = getDirectionBitsOpen(ant);
            String bits = toBinaryString(dirBits);
            int direction = -1;
            int length = bits.length();
            
            if (length < 8 || bits.charAt(length - 1) == '0')
            {
              for (int i = length - 1; i > -1; i--)
              {
                if (bits.charAt(i) == '1')
                {
                  direction = i + (8 - length);
                  break;
                }
              }
            }
            
            if (direction == -1)
            {
              for (int i = 0; i < 8; i++)
              {
                if (bits.charAt(i) == '0')
                {
                  direction = i - 1;
                  break;
                }
              }
            }
            
            if (direction == 0) action.direction = Direction.NORTHWEST;
            if (direction == 1) action.direction = Direction.WEST;
            if (direction == 2) action.direction = Direction.SOUTHWEST;
            if (direction == 3) action.direction = Direction.SOUTH;
            if (direction == 4) action.direction = Direction.SOUTHEAST;
            if (direction == 5) action.direction = Direction.EAST;
            if (direction == 6) action.direction = Direction.NORTHEAST;
            if (direction == 7) action.direction = Direction.NORTH;
            
            action.type = AntActionType.MOVE;
            return true;
          }
        }
      }
    }
    return false;
  }
  
  /**
   * Explore the map in pulses. Go out to a certain distance, rotate to the right, and then come back to base.
   * With every pulse, the exploration distance gets larger. This method guarantees that if food spawns close to
   * the base and the ants have passed it on their way out, they will find it on the way back.
   * @param ant current ant
   * @param action action of ant
   * @param data comm data
   * @return pulse-like movement for the ant
   */
  private boolean goExplore(AntData ant, AntAction action, CommData data)
  {
    int distance = manhattanDistance(centerX, centerY, ant.gridX, ant.gridY);
    boolean notReturning = true;

    if (historyForExploring.contains(ant.id)) notReturning = false;
    
    double angle = atan2(ant.gridY - centerY,ant.gridX - centerX);

    int goalX = intValue(100000 * cos(angle)) + random.nextInt(400) - 200;
    int goalY = intValue(100000 * sin(angle)) + random.nextInt(400) - 200;
    antsExploring++;

    if (DEBUG) System.out.println("antID: " + ant.id + " angle" + angle);

    if (ant.antType != AntType.SPEED)
    {
      if (setPreviousTick && notReturning && distance >= explorationDistance)
      {
        isTwisting = true;
        previousTick = data.gameTick;
        setPreviousTick = false;
        lastExplorationDistance = explorationDistance;
        lastExplorationTwistTick = explorationTwistTick;
      }
      else if (isTwisting)
      {

        if (DEBUG) System.out.println("previousTick" + previousTick);
        if (data.gameTick <= previousTick + explorationTwistTick)
        {
          if (!historyForExploring.contains(ant.id))
          {
            historyForExploring.add(ant.id);
          }
          goalX = intValue(100000 * cos(angle + Math.PI / 2));
          goalY = intValue(100000 * sin(angle + Math.PI / 2));
        }
        else if (!notReturning && distance > 45)
        {
          goalX = intValue(100000 * cos(angle + Math.PI)) + random.nextInt(200) - 100;
          goalY = intValue(100000 * sin(angle + Math.PI)) + random.nextInt(200) - 100;
        }
        else
        {
          int i;
          for (i = 0; i < historyForExploring.size() - 1; i++)
          {
            if (historyForExploring.get(i) == ant.id)
            {
              historyForExploring.remove(i);
              break;
            }
          }

          if (historyForExploring.size() < antsExploring / 2)
          {
            isTwisting = false;
            setPreviousTick = true;
            if (lastExplorationDistance == explorationDistance && lastExplorationTwistTick == explorationTwistTick)
            {
              if (explorationDistance <= 1000)
              {
                explorationDistance += 100;
                explorationTwistTick += 30;
              }
            }
          }
        }
      }
    }

    if (notReturning && unStickOnWater(ant, action))
    {
      return true;
    }
    return goToward(ant, goalX, goalY, action);
  }
  
  /**
   * Go in a random direction that is open
   * @param ant current ant
   * @param action action of ant
   * @return random movement of the ant
   */
  private boolean goRandom(AntData ant, AntAction action)
  {
    int dirBits = getDirectionBitsOpen(ant);
    Direction dir = getRandomDirection(dirBits);
    if (dir == null) return false;
    action.type = AntActionType.MOVE;
    action.direction = dir;
    return true;
  }
  
  /**
   * Go through all of the possibilities for the ant actions and choose one for the current ant
   * @param data comm data
   * @param ant current ant
   * @return the action for that ant
   */
  private AntAction chooseAction(CommData data, AntData ant)
  {
    AntAction action = new AntAction(AntActionType.STASIS);
    if (ant.ticksUntilNextAction > 0) return action;
    if (exitNest(ant, action, data)) return action;
    if (unStickAnts(ant, action, data)) return action;
    if (goHomeIfCarryingOrHurt(ant, action, data)) return action;
    if (goToFood(ant, action, data)) return action;
    if (pickUpWater(ant, action, data)) return action;
    if (goToEnemyAnt(ant, action, data)) return action;
    if (goExplore(ant, action, data)) return action;
    if (goRandom(ant, action)) return action;
    return action;
  }

  public static void main(String[] args)
  {
    String serverHost = "localhost";
    if (args.length > 0) serverHost = args[0];
    System.out.println("Starting client with connection to: " + serverHost);

    new JoshAntonAntAI(serverHost, Constants.PORT);
  }
}
