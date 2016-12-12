package antworld.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

import antworld.common.*;
import antworld.common.AntAction.AntActionType;

import static java.lang.Integer.toBinaryString;
import static java.lang.Math.*;
import static oracle.jrockit.jfr.events.Bits.intValue;

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
  private Queue<location> exitQueue = new LinkedList<>();
  private ArrayList<AntData> waterAnts = new ArrayList<>();
  private ArrayList<AntHistory> antHistories = new ArrayList<>();
  private ArrayList<Integer> historyForExploring = new ArrayList<>();
  
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
    for(AntData ant : data.myAntList)
    {
      antHistories.add(new AntHistory(ant.id));
    }
    
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

  private void chooseActionsOfAllAnts(CommData data)
  {
    for (AntData ant : data.myAntList)
    {
      AntAction action = chooseAction(data, ant);
      ant.myAction = action;
    }
    //Spawn attack ant
    //AntData attackAnt = new AntData(-1, AntType.ATTACK, myNestName, myTeam);
    //data.myAntList.add(attackAnt);
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
  private boolean exitNest(AntData ant, AntAction action, CommData data)
  {
    if (ant.underground == false) return false;
    if (ant.carryUnits > 0) return false;
    if (ant.health < ant.antType.getMaxHealth()) return false;

    action.type = AntActionType.EXIT_NEST;

    if(firstExit)
    {
      double exitIncrement = 6 * Math.PI/data.myAntList.size();

      if(exitCountForInitial >= data.myAntList.size()) firstExit = false;

      if(exitAngle < 2 * Math.PI)
      {
        action.x = centerX + intValue(15 * cos(exitAngle));
        action.y = centerY + intValue(15 * sin(exitAngle));
        exitAngle += exitIncrement;
      }
      else if(exitAngle < 4 * Math.PI)
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
      if(!exitQueue.isEmpty())
      {
        location loc = exitQueue.remove();
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

  private boolean unStickAnts(AntData ant, AntAction action, CommData data)
  {
    int neighborCounter = 0;
    for(AntData neighborAnt : data.myAntList)
    {
      if(manhattanDistance(ant.gridX, ant.gridY, neighborAnt.gridX, neighborAnt.gridY) < 2 && data.gameTick > 200)
      {
        neighborCounter++;
      }
    }
    
    if(neighborCounter > 2)
    {
      int dirBits = getDirectionBitsOpen(ant);
      Direction dir = getRandomDirection(dirBits);
      action.type = AntActionType.MOVE;
      action.direction = dir;
      return true;
    }
    return false;
  }
  
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
  
  private boolean goHome(AntData ant, AntAction action, int homeAction, CommData data)
  {
    if(homeAction == 1 && ant.underground)
    {
      action.type = AntActionType.HEAL;
      System.out.println("Healed");
      return true;
    }
    
    if(homeAction == 2 && ant.underground)
    {
      action.type = AntActionType.DROP;
      action.direction = Direction.NORTH;
      action.quantity = ant.carryUnits;
      
      //Print out nest food supply
      for(int i = 0; i < data.foodStockPile.length; i++)
      {
        System.out.println(data.foodStockPile[i]);
      }
      return true;
    }

    int distance = manhattanDistance(ant.gridX, ant.gridY, centerX, centerY);

    if(distance < 21)
    {
      exitQueue.add(new location(ant.gridX, ant.gridY));
      action.type = AntActionType.ENTER_NEST;
      System.out.println("Entered nest");
      return true;
    }
    
    return goToward(ant, centerX, centerY, action);
  }
  
  private boolean goHomeIfCarryingOrHurt(AntData ant, AntAction action, CommData data)
  {
    if(ant.health <= ant.antType.getMaxHealth()/1.8)
    {
      return goHome(ant, action, 1, data);
    }
    else if(ant.carryUnits > 10 && ant.carryType != FoodType.WATER)
    {
      return goHome(ant, action, 2, data);
    }
    else if(ant.carryUnits >= ant.antType.getCarryCapacity() && ant.carryType == FoodType.WATER)
    {
      return goHome(ant, action, 2, data);
    }
    return false;
  }
  
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
      
      if(neighborCell.getX() == food.gridX && neighborCell.getY() == food.gridY)
      {
        action.type = AntActionType.PICKUP;
        action.direction = dir;
        if(food.getCount() < ant.antType.getCarryCapacity())
        {
          System.out.println("Picked up leftovers");
          action.quantity = food.getCount();
          return true;
        }
        else
        {
          System.out.println("Picked up everything");
          action.quantity = ant.antType.getCarryCapacity();
          return true;
        }
      }
    }
    return false;
  }
  
  private boolean goToFood(AntData ant, AntAction action, CommData data)
  {
    if(!data.foodSet.isEmpty())
    {
      for (FoodData food : data.foodSet)
      {
        int distance = manhattanDistance(ant.gridX, ant.gridY, food.gridX, food.gridY);
        if(distance < 2)
        {
          return pickUpFoodAdjacent(ant, action, food);
        }
        
        if(distance < 40)
        {
          if(ant.carryType == FoodType.WATER)
          {
            action.type = AntActionType.DROP;
            action.direction = Direction.NORTH;
            action.quantity = ant.carryUnits;
            return true;
          }
          return goToward(ant, food.gridX, food.gridY, action);
        }
      }
    }
    return false;
  }
  
  private boolean pickUpWater(AntData ant, AntAction action, CommData data)
  {
    if (DEBUG) System.out.println("  pickUpWater()");

    if(data.foodStockPile[0] > 5000) return false;

    int distance = manhattanDistance(ant.gridX, ant.gridY, waterX, waterY);
    
    if (distance < 45 && waterAnts.size() < 10)
    {
      waterAnts.add(ant);
      System.out.println("Adding ant to list for water");
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
        if(waterX == -1 || waterY == -1)
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
  
  private boolean attackAdjacent(AntData ant, AntAction action, AntData enemyAnt)
  {
    if (DEBUG) System.out.println("  attackAdjacent()");
    System.out.println("Calling attack");
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
        System.out.println("Attacking ant");
        return true;
      }
    }
    return false;
  }

  private boolean goToEnemyAnt(AntData ant, AntAction action, CommData data)
  {
    if(!data.enemyAntSet.isEmpty())
    {
      for(AntData enemyAnt : data.enemyAntSet)
      {
        int distance = manhattanDistance(ant.gridX, ant.gridY, enemyAnt.gridX, enemyAnt.gridY);

        if(distance < 2)
        {
          return attackAdjacent(ant, action, enemyAnt);
        }

        if(distance < 40)
        {
          for (AntHistory history : antHistories)
          {
            if (ant.id == history.getAntID())
            {
              if (history.getEnemyAnt() != null)
              {
                history.setEnemyAnt(enemyAnt);

                System.out.println(ant.id + " going to enemy ant " + history.getEnemyAnt().id);
                return goToward(ant, history.getEnemyAnt().gridX, history.getEnemyAnt().gridY, action);
              }

              if (distance < 40 && history.getEnemyAnt() == null)
              {
                System.out.println(ant.id + " setting new enemy ant " + enemyAnt.id);
                history.setEnemyAnt(enemyAnt);
              }
            }
          }
        }

        //for (AntHistory history : antHistories)
        //{
        //  if (history.getAntID() == ant.id && distance > 40 && history.getEnemyAnt() != null)
        //  {
        //    System.out.println("********************** resetting ant id for " + ant.id);
        //    history.setEnemyAnt(null);
        //  }
        //}
      }
    }
    return false;
  }
  
  private boolean unStickOnWater(AntData ant, AntAction action)
  {
    if(!waterAnts.contains(ant))
    {
      for(int x = ant.gridX - 1; x < ant.gridX + 2; x++)
      {
        for(int y = ant.gridY - 1; y < ant.gridY + 2; y++)
        {
          if(map[x][y].getType() == 'W')
          {
            int dirBits = getDirectionBitsOpen(ant);
            String bits = toBinaryString(dirBits);
            int direction = -1;
            int length = bits.length();
            
            if(length < 8 || bits.charAt(length - 1) == '0')
            {
              for(int i = length - 1; i > -1; i--)
              {
                if(bits.charAt(i) == '1')
                {
                  direction = i + (8 - length);
                  break;
                }
              }
            }
            
            if(direction == -1)
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
            
            if(direction == 0) action.direction = Direction.NORTHWEST;
            if(direction == 1) action.direction = Direction.WEST;
            if(direction == 2) action.direction = Direction.SOUTHWEST;
            if(direction == 3) action.direction = Direction.SOUTH;
            if(direction == 4) action.direction = Direction.SOUTHEAST;
            if(direction == 5) action.direction = Direction.EAST;
            if(direction == 6) action.direction = Direction.NORTHEAST;
            if(direction == 7) action.direction = Direction.NORTH;
            
            action.type = AntActionType.MOVE;
            return true;
          }
        }
      }
    }
    return false;
  }
  
  private boolean goExplore(AntData ant, AntAction action, CommData data)
  {

    int distance = manhattanDistance(centerX, centerY, ant.gridX, ant.gridY);
    boolean notReturning = true;

    if(historyForExploring.contains(ant.id)) notReturning = false;
  
    if(notReturning && unStickOnWater(ant, action))
    {
      return true;
    }
    
    double angle = atan2(ant.gridY - centerY,ant.gridX - centerX);

    int goalX = intValue(100000 * cos(angle));
    int goalY = intValue(100000 * sin(angle));

    //System.out.println("antID: " + ant.id + " angle" + angle);

    if(setPreviousTick && notReturning && distance >= explorationDistance)
    {
      isTwisting = true;
      previousTick = data.gameTick;
      setPreviousTick = false;
      lastExplorationDistance = explorationDistance;
      lastExplorationTwistTick = explorationTwistTick;
    }
    else if(isTwisting)
    {
      //System.out.println("previousTick" + previousTick);
      if(data.gameTick <= previousTick + explorationTwistTick)
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
        return goToward(ant, centerX, centerY, action);
      }
      else
      {

        int i;

        for(i = 0; i < historyForExploring.size() - 1; i++)
        {
          if(historyForExploring.get(i) == ant.id)
          {
            historyForExploring.remove(i);
            break;
          }
        }

        if(historyForExploring.size() < data.myAntList.size()/10)
        {
          isTwisting = false;
          setPreviousTick = true;
          if (lastExplorationDistance == explorationDistance && lastExplorationTwistTick == explorationTwistTick)
          {
            if(explorationDistance <= 800)
            {
              explorationDistance += 100;
              explorationTwistTick += 30;
            }
          }
        }
      }
    }

    return goToward(ant, goalX, goalY, action);
  }
  
  private boolean goRandom(AntData ant, AntAction action)
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
    if (exitNest(ant, action, data)) return action;
    //if (data.gameTick < 150) return new AntAction(AntActionType.STASIS);
    if (unStickAnts(ant, action, data)) return action;
    //if (goHomeIfCarryingOrHurt(ant, action, data)) return action;
    //if (goToFood(ant, action, data)) return action;
    //if (pickUpWater(ant, action, data)) return action;
    //if (goToEnemyAnt(ant, action, data)) return action;
    //if (unStickOnWater(ant, action)) return action;
    if (goExplore(ant, action, data)) return action;
    //if (goRandom(ant, action)) return action;
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
