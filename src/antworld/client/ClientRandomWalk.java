package antworld.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

import antworld.common.*;
import antworld.common.AntAction.AntActionType;
import antworld.server.Ant;

public class ClientRandomWalk
{
  private static final boolean DEBUG = false;
  private static final TeamNameEnum myTeam = TeamNameEnum.Josh_Anton;
  private static final long password = 962740848319L;//Each team has been assigned a random password.
  private ObjectInputStream inputStream = null;
  private ObjectOutputStream outputStream = null;
  private boolean isConnected = false;
  private NestNameEnum myNestName = null;
  private int centerX, centerY;

  private World world = new World();
  private Pixel[][] map = world.getWorld();
  private static final int DIR_BIT_N  = 1;
  private static final int DIR_BIT_NE = 2;
  private static final int DIR_BIT_E  = 4;
  private static final int DIR_BIT_SE = 8;
  private static final int DIR_BIT_S  = 16;
  private static final int DIR_BIT_SW = 32;
  private static final int DIR_BIT_W  = 64;
  private static final int DIR_BIT_NW = 128;
  private static final int DIR_BIT_ANY_N = DIR_BIT_N | DIR_BIT_NE | DIR_BIT_NW;
  private static final int DIR_BIT_ANY_S = DIR_BIT_S | DIR_BIT_SE | DIR_BIT_SW;
  private static final int DIR_BIT_ANY_E = DIR_BIT_E | DIR_BIT_NE | DIR_BIT_SE;
  private static final int DIR_BIT_ANY_W = DIR_BIT_W | DIR_BIT_NW | DIR_BIT_SW;
  
  private Socket clientSocket;

  //A random number generator is created in Constants. Use it.
  //Do not create a new generator every time you want a random number nor
  //  even in every class were you want a generator.
  private static Random random = Constants.random;

  public ClientRandomWalk(String host, int portNumber)
  {
    System.out.println("Starting ClientRandomWalk: " + System.currentTimeMillis());
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
      System.err.println("ClientRandomWalk Error: Unknown Host " + host);
      e.printStackTrace();
      return false;
    }
    catch (IOException e)
    {
      System.err.println("ClientRandomWalk Error: Could not open connection to " + host + " on port " + portNumber);
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
      System.err.println("ClientRandomWalk Error: Could not open i/o streams");
      e.printStackTrace();
      return false;
    }
    return true;
  }

  public void closeAll()
  {
    System.out.println("ClientRandomWalk.closeAll()");
    {
      try
      {
        if (outputStream != null) outputStream.close();
        if (inputStream != null) inputStream.close();
        clientSocket.close();
      }
      catch (IOException e)
      {
        System.err.println("ClientRandomWalk Error: Could not close");
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
          if (DEBUG) System.out.println("ClientRandomWalk: listening to socket....");
          data = (CommData) inputStream.readObject();
          if (DEBUG) System.out.println("ClientRandomWalk: received <<<<<<<<<"+inputStream.available()+"<...\n" + data);
          
          if (data.errorMsg != null)
          {
            System.err.println("ClientRandomWalk***ERROR***: " + data.errorMsg);
            System.exit(0);
          }
        }
        catch (IOException e)
        {
          System.err.println("ClientRandomWalk***ERROR***: client read failed");
          e.printStackTrace();
          System.exit(0);
        }
        catch (ClassNotFoundException e)
        {
          System.err.println("ClientRandomWalk***ERROR***: client sent incorrect common format");
        }
      }
    if (data.myTeam != myTeam)
    {
      System.err.println("ClientRandomWalk***ERROR***: Server returned wrong team name: "+data.myTeam);
      System.exit(0);
    }
    if (data.myNest == null)
    {
      System.err.println("ClientRandomWalk***ERROR***: Server returned NULL nest");
      System.exit(0);
    }

    myNestName = data.myNest;
    centerX = data.nestData[myNestName.ordinal()].centerX;
    centerY = data.nestData[myNestName.ordinal()].centerY;
    System.out.println("ClientRandomWalk: ==== Nest Assigned ===>: " + myNestName);
    return data;
  }
    
  public void mainGameLoop(CommData data)
  {
    while (true)
    { 
      try
      {
        if (DEBUG) System.out.println("ClientRandomWalk: chooseActions: " + myNestName);

        chooseActionsOfAllAnts(data);  

        CommData sendData = data.packageForSendToServer();
        
//        System.out.println("ClientRandomWalk: Sending>>>>>>>: " + sendData);
        outputStream.writeObject(sendData);
        outputStream.flush();
        outputStream.reset();
        
        if (DEBUG) System.out.println("ClientRandomWalk: listening to socket....");
        CommData receivedData = (CommData) inputStream.readObject();
        if (DEBUG) System.out.println("ClientRandomWalk: received <<<<<<<<<"+inputStream.available()+"<...\n" + receivedData);
        data = receivedData;
        
        if ((myNestName == null) || (data.myTeam != myTeam))
        {
          System.err.println("ClientRandomWalk: !!!!ERROR!!!! " + myNestName);
        }
      }
      catch (IOException e)
      {
        System.err.println("ClientRandomWalk***ERROR***: client read failed");
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
      if (DEBUG) System.out.println("ClientRandomWalk.sendCommData(" + sendData +")");
      outputStream.writeObject(sendData);
      outputStream.flush();
      outputStream.reset();
    }
    catch (IOException e)
    {
      System.err.println("ClientRandomWalk***ERROR***: client read failed");
      e.printStackTrace();
      System.exit(0);
    }
    return true;
  }

  private void chooseActionsOfAllAnts(CommData commData)
  {
    for (AntData ant : commData.myAntList)
    {
      AntAction action = chooseAction(commData, ant);
      ant.myAction = action;
    }
    //for(int i = 0; i < commData.foodStockPile.length; i++)
    //{
    //  System.out.println(commData.foodStockPile[i]);
    //}
  }
  
  private int manhattanDistance(int x, int y, int xx, int yy)
  {
    return Math.abs(x - xx) + Math.abs(y - yy);
  }

  //=============================================================================
  // This method sets the given action to EXIT_NEST if and only if the given
  //   ant is underground.
  // Returns true if an action was set. Otherwise returns false
  //=============================================================================
  private boolean exitNest(AntData ant, AntAction action)
  {
    if (ant.underground == false) return false;
    
    action.type = AntActionType.EXIT_NEST;
    action.x = centerX - (Constants.NEST_RADIUS-1) + random.nextInt(2 * (Constants.NEST_RADIUS-1));
    action.y = centerY - (Constants.NEST_RADIUS-1) + random.nextInt(2 * (Constants.NEST_RADIUS-1));
    return true;
  }

  private boolean attackAdjacent(AntData ant, AntAction action)
  {
    return false;
  }

  private boolean pickUpFoodAdjacent(AntData ant, AntAction action, FoodData food)
  {
    if (DEBUG) System.out.println("  pickUpFoodAdjactent()");
    if (ant.carryUnits > 0) return false;
    
    System.out.println("Food:" + food.gridX + " " + food.gridY);
    
    for (Direction dir : Direction.values())
    {
      int x = ant.gridX + dir.deltaX();
      int y = ant.gridY + dir.deltaY();
      Pixel neighborCell = map[x][y];
      
      if (neighborCell == null) continue;
      
      if (neighborCell.getType() == 'W') continue;

      System.out.println("Cell:" + neighborCell.getX() + " " + neighborCell.getY());
      
      if(neighborCell.getX() == food.gridX && neighborCell.getY() == food.gridY)
      {
        System.out.println("Neighbor cell found food");
        action.type = AntActionType.PICKUP;
        action.direction = dir;
        if(food.getCount() < ant.antType.getCarryCapacity())
        {
          System.out.println("Picked up small food");
          action.quantity = food.getCount();
        }
        else
        {
          System.out.println("Picked up everything");
          action.quantity = ant.antType.getCarryCapacity();
        }
      }
      return true;
    }
    return false;
  }
  
  public boolean goHome(AntData ant, AntAction action, int homeAction)
  {
    if(homeAction == 1 && ant.gridX < centerX + 10 && ant.gridX > centerX - 10 && ant.gridY < centerY + 10 && ant.gridY > centerY - 10)
    {
      action.type = AntActionType.ENTER_NEST;
      action.type = AntActionType.HEAL;
      System.out.println("Healed");
      return true;
    }
    
    if(homeAction == 2 && ant.gridX < centerX + 10 && ant.gridX > centerX - 10 && ant.gridY < centerY + 10 && ant.gridY > centerY - 10)
    {
      action.type = AntActionType.DROP;
      action.direction = Direction.NORTH;
      action.quantity = ant.carryUnits;
      System.out.println("Dropped food");
      return true;
    }
    
    return goToward(ant, centerX, centerY, action);
  }

  private boolean goHomeIfCarryingOrHurt(AntData ant, AntAction action)
  {
    if(ant.health <= ant.antType.getMaxHealth()/1.8)
    {
      return goHome(ant, action, 1);
    }
    else if(ant.carryUnits >= ant.antType.getCarryCapacity()/2)
    {
      return goHome(ant ,action, 2);
    }
    
    return false;
  }

  private boolean pickUpWater(AntData ant, AntAction action)
  {
    if (DEBUG) System.out.println("  pickUpWater()");
    
    if (ant.carryUnits > 0)
    {
      if (ant.carryUnits >= ant.antType.getCarryCapacity()) return false;
      if (ant.carryType != FoodType.WATER) return false;
    }
    
    for (Direction dir : Direction.values())
    {
      int x = ant.gridX + dir.deltaX();
      int y = ant.gridY + dir.deltaY();
      Pixel neighborCell = map[x][y];
    
      if (neighborCell == null) continue;
    
      if (neighborCell.getType() == 'W')
      {
        action.type = AntActionType.PICKUP;
        action.direction = dir;
        action.quantity = ant.antType.getCarryCapacity()/2;
        return true;
      }
    }
    return false;
  }

  private boolean goToEnemyAnt(AntData ant, AntAction action)
  {
    return false;
  }

  private boolean goToFood(AntData ant, AntAction action, CommData data)
  {
    if(!data.foodSet.isEmpty())
    {
      for (FoodData food : data.foodSet)
      {
        if(manhattanDistance(ant.gridX, ant.gridY, food.gridX, food.gridY) < 1)
        {
          System.out.println("One block away");
          return pickUpFoodAdjacent(ant, action, food);
        }
        
        if(manhattanDistance(ant.gridX, ant.gridY, food.gridX, food.gridY) < 40)
        {
          return goToward(ant, food.gridX, food.gridY, action);
        }
      }
    }
    return false;
  }

  private boolean goToGoodAnt(AntData ant, AntAction action)
  {
    return false;
  }
  
  private boolean goExplore(AntData ant, AntAction action)
  {
    int goalX = 0;
    int goalY = 0;
    if (ant.gridX > centerX) goalX = 1000000;
    if (ant.gridY > centerY) goalY = 1000000;
    int dirBits = getDirectionBitsOpen(ant);
    dirBits = getDirBitsToLocation(dirBits, ant.gridX, ant.gridY, goalX, goalY);
  
    if (action.type == AntActionType.MOVE)
    {
      int dx = action.direction.deltaY();
      int dy = action.direction.deltaY();
      int lastGoalX = goalX;
      int lastGoalY = goalY;
      if (dx != 0) lastGoalX = ant.gridX + dx;
      if (dy != 0) lastGoalY = ant.gridY + dy;
    
      dirBits = getDirBitsToLocation(dirBits, ant.gridX, ant.gridY, lastGoalX, lastGoalY);
    }
    
    if (dirBits == 0) return false;
    
    return goToward(ant, goalX, goalY, action);
  }
  
  public boolean goToward(AntData ant, int x, int y, AntAction action)
  {
    int dirBits = getDirectionBitsOpen(ant);
    dirBits = getDirBitsToLocation(dirBits, ant.gridX, ant.gridY, x, y);
    Direction dir = getRandomDirection(dirBits);
    
    if (dir == null) return false;
    action.type = AntActionType.MOVE;
    action.direction = dir;
    return true;
  }
  
  public static Direction getRandomDirection(int dirBits)
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
      else if (neighborCell.getType() != 'G' && ant.gridX > centerX + 10 && ant.gridX < centerX - 10 && ant.gridY > centerY + 10 && ant.gridY < centerY - 10)  dirBits -= bit;
    }
    
    return dirBits;
  }
  
  public static int getDirBitsToLocation(int dirBits, int x, int y, int xx, int yy)
  {
    if (xx <= x) dirBits = dirBits & (~DIR_BIT_ANY_E);
    if (xx >= x) dirBits = dirBits & (~DIR_BIT_ANY_W);
    if (yy <= y) dirBits = dirBits & (~DIR_BIT_ANY_S);
    if (yy >= y) dirBits = dirBits & (~DIR_BIT_ANY_N);
    return dirBits;
  }
  
  public boolean goRandom(AntData ant, AntAction action)
  {
    int dirBits = getDirectionBitsOpen(ant);
    Direction dir = getRandomDirection(dirBits);
    if (dir == null) return false;
    action.type = AntActionType.MOVE;
    action.direction = dir;
    return true;
  }

  private AntAction chooseAction(CommData data, AntData ant)
  {
    AntAction action = new AntAction(AntActionType.STASIS);
    
    if (ant.ticksUntilNextAction > 0) return action;
    if (exitNest(ant, action)) return action;
    if (goHomeIfCarryingOrHurt(ant, action)) return action;
    //if (pickUpFoodAdjacent(ant, action, data)) return action;
    if (goToFood(ant, action, data)) return action;
    //if (pickUpWater(ant, action)) return action;
    //if (attackAdjacent(ant, action)) return action;
    //if (goToEnemyAnt(ant, action)) return action;
    //if (goToGoodAnt(ant, action)) return action;
    if (goExplore(ant, action)) return action;
    if (goRandom(ant, action)) return action;
    return action;
  }

  public static void main(String[] args)
  {
    String serverHost = "localhost";
    if (args.length > 0) serverHost = args[0];
    System.out.println("Starting client with connection to: " + serverHost);

    new ClientRandomWalk(serverHost, Constants.PORT);
  }
}
