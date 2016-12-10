package antworld.server;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;

import antworld.common.Util;
import antworld.common.AntAction;
import antworld.common.AntAction.AntActionType;
import antworld.common.AntData;
import antworld.common.AntType;
import antworld.common.CommData;
import antworld.common.Constants;
import antworld.common.FoodData;
import antworld.common.FoodType;
import antworld.common.NestData;
import antworld.common.NestNameEnum;
import antworld.common.TeamNameEnum;

public class Nest extends NestData implements Serializable
{
  private static final long serialVersionUID = Constants.VERSION;
 
  public enum RequireBirthResource {TRUE, FALSE};
  public enum NetworkStatus {CONNECTED, DISCONNECTED, UNDERGROUND};
  
  
  private static int idCounter = 0;
  public static int INVALID_NEST_ID = -1;

  private int[] foodStockPile = new int[FoodType.SIZE];

  private ArrayList<AntData> antList = new ArrayList<>();
  
  private volatile long timeOfLastMessageFromClient;
  private NetworkStatus status = NetworkStatus.CONNECTED; 
  
  
  
  private static AntData tmpAntData = new AntData(Constants.UNKNOWN_ANT_ID, AntType.WORKER, null, null);
  

  
  

  public Nest(NestNameEnum nestName, int x, int y)
  {
    super(nestName, TeamNameEnum.NEARLY_BRAINLESS_BOTS, x, y);
    idCounter++;

    foodStockPile[FoodType.WATER.ordinal()] = Constants.INITIAL_NEST_WATER_UNITS;
    spawnInitialAnts(null, TeamNameEnum.NEARLY_BRAINLESS_BOTS);
    
  }
  
  
  public static NestData deepCopyNestData(NestData source)
  {
    NestData target = new NestData(source.nestName, source.team, source.centerX, source.centerY);
    target.score = source.score;
    return target;
  }
    


  public void setTeam(TeamNameEnum team)
  {
    this.team = team;
  }

  public static int getNextID()
  {
    return idCounter;
  }

  public int getCenterX()
  {
    return centerX;
  }

  public int getCenterY()
  {
    return centerY;
  }

  public int getFoodStockPile(FoodType type)
  {
    return foodStockPile[type.ordinal()];
  }

  public ArrayList<AntData> getAntList()
  {
    return antList;
  }

  public void addFoodStockPile(FoodType type, int quantity)
  {
    foodStockPile[type.ordinal()] += quantity;
  }
  
  
  
  public int[] copyFoodStockPile()
  {
    int[] copy = new int[FoodType.SIZE];
    for (int i=0; i<FoodType.SIZE; i++)
    { copy[i] = foodStockPile[i];
    }
    return copy;
  }

  public void consumeFood(FoodType type, int quantity)
  {
    foodStockPile[type.ordinal()] -= quantity;
  }
  
  public int calculateScore()
  {
    int score = 0;
    for (FoodType type : FoodType.values())
    { 
      if (type != FoodType.WATER) score += foodStockPile[type.ordinal()];
    }
    for (AntData ant : antList)
    {
      if (ant.alive) score+= AntType.TOTAL_FOOD_UNITS_TO_SPAWN/2;
    }
    return score;
  }
  
  public long getTimeOfLastMessageFromClient() {return timeOfLastMessageFromClient;}
  public void receivedMessageFromClient()
  {
    timeOfLastMessageFromClient = System.currentTimeMillis();
  }

  public void spawnInitialAnts(AntWorld world, TeamNameEnum myTeam)
  {
    this.team = myTeam;
    
    if (world != null)
    { for (AntData ant : antList)
      {
        world.removeAnt(ant);
      }
    }
    antList.clear();
    for (int i = 0; i < Constants.INITIAL_ANT_SPAWN_COUNT; i++)
    {
      spawnAnt(AntType.WORKER, RequireBirthResource.FALSE);
    }
  }
  
  private int spawnAnt(AntType antType, RequireBirthResource requireResource)
  {
    if (requireResource == RequireBirthResource.TRUE)
    {
      FoodType[] neededFoodList = antType.getBirthFood();

      for (FoodType food : neededFoodList)
      {
        if (getFoodStockPile(food) < antType.getFoodUnitsToSpawn(food)) return Ant.INVALID_ANT_ID;
      }

      //If this line is reached, the nest has the required food.
      for (FoodType food : neededFoodList)
      {
        consumeFood(food, antType.getFoodUnitsToSpawn(food));
      }
    }

    AntData ant = Ant.createAnt(antType, nestName, team);
    ant.gridX = centerX;
    ant.gridY = centerY;
    antList.add(ant);

    return ant.id;
  }

  
  public void sendAllAntsUnderground(AntWorld world)
  {
    status = NetworkStatus.UNDERGROUND;
    for (AntData ant : antList)
    {
      world.removeAnt(ant);
      ant.underground = true;
      ant.gridX = centerX;
      ant.gridY = centerY;
    }
  }
  
  public void setNetworkStatus(NetworkStatus status)
  {
    if ((status == NetworkStatus.DISCONNECTED) && (this.status == NetworkStatus.UNDERGROUND)) return;
    
    this.status = status;
  }
  
  public NetworkStatus getNetworkStatus() {return status;}
  
  
  public boolean isInNest(int x, int y)
  { if (Util.manhattanDistance(centerX, centerY, x, y) <= Constants.NEST_RADIUS) return true;
    return false;
  }
  
  
  public void updateRemoveDeadAntsFromAntList()
  {
    Iterator<AntData> iterator = antList.iterator();
    while (iterator.hasNext())
    {
      AntData ant = iterator.next();
      if (ant.alive) ant.myAction.type = AntActionType.STASIS;
      else iterator.remove();
    }
  }

  public void updateReceive(AntWorld world, CommData commData)
  {
    //System.out.println("Nest.updateReceive()==========================["+team+"]:"+commData.myAntList.size());
    // receiving common from client
    if (team == TeamNameEnum.EMPTY) return;
    

    
    if (commData.myAntList == null) return;
    
    
//    if (nestName == NestNameEnum.HARVESTER)
//    { AntData ant = getAntList().get(0);
//      System.out.println(ant);
//    }
        
    
    
    //for (AntData clientAnt : commData.myAntList)
    for (int idx=0; idx<commData.myAntList.size(); idx++)
    {
      AntData clientAnt = commData.myAntList.get(idx);
      
      if (clientAnt.id == Constants.UNKNOWN_ANT_ID)
      {
        if (clientAnt.myAction.type != AntActionType.BIRTH) continue;
        
        int antID = spawnAnt(clientAnt.antType, RequireBirthResource.TRUE);
        System.out.println("Nest.updateRecv() BIRTH antID=" + antID);
        continue;
      }
      
      boolean legalAnt = false;
      AntData serverAnt = null;
      //int index = Collections.binarySearch(antList, clientAnt);
      int serverIdx = idx;
      if (serverIdx < antList.size())
      { serverAnt = antList.get(serverIdx);
        if (serverAnt.id == clientAnt.id) legalAnt = true;
      }
      if (!legalAnt)
      { for (AntData ant : antList)
        {
          if (ant.id == clientAnt.id) 
          { 
            legalAnt = true;
            serverAnt = ant;
            break;
          }
        }
      }
      
      
      //System.out.println("Nest.updateRecv() ant index=" + index);
      if (!legalAnt)
      { 
        System.out.println("Nest.updateRecv() ant Illegal Ant =" + clientAnt);
        continue;
      }
      
      boolean okay = Ant.update(world, serverAnt, clientAnt.myAction);
      if (okay)
      { serverAnt.myAction.copyFrom(clientAnt.myAction);
      }
    }
  }

  public void updateRemoveDeadAntsFromWorld(AntWorld world)
  {
    for (AntData ant : antList)
    {
      if (!ant.alive)
      {
        world.removeAnt(ant);
        FoodType foodType = AntType.getDeadAntFoodType();
        int foodUnits = AntType.getDeadAntFoodUnits();
        if (ant.carryUnits > 0)
        {
          foodType = ant.carryType;
          foodUnits = ant.carryUnits;
        }
  
        FoodData droppedFood = new FoodData(foodType, ant.gridX, ant.gridY, foodUnits);
        world.addFood(null, droppedFood);
        //System.out.println("Nest.update() an Ant had died: Current Ant Populatuion = " + antList.size());
        ant.myAction.type = AntAction.AntActionType.DIED;
      }
    }
  }

  public AntData getAntByID(int antId)
  {
    tmpAntData.id = antId;
    int index = Collections.binarySearch(antList, tmpAntData);
    return antList.get(index);
  }

  public CommData updateSendPacket(AntWorld world, CommData orgCommData)
  {
    // sending common to client
    
    CommData commData = new CommData(team);
    
    if (orgCommData.requestNestData)
    {
      commData.nestData = world.createNestDataList();
    }
    else commData.nestData = null;

    commData.myNest = nestName;
    commData.gameTick = AntWorld.getGameTick()+1;
    commData.wallClockMilliSec = world.getWallClockAtLastUpdateStart();
    commData.myAntList.clear();
    commData.enemyAntSet = new HashSet<AntData>();
    commData.foodSet = new HashSet<FoodData>();
    commData.foodStockPile = foodStockPile;
    
    //System.out.println("foodStockPile="+Util.arrayToString(foodStockPile));
    //System.out.println("        this="+System.identityHashCode(this));
    
    for (AntData ant : antList)
    {
      commData.myAntList.add(ant);
      
      world.appendAntsInProximity(ant, commData.enemyAntSet);
      world.appendFoodInProximity(ant, commData.foodSet);
    }
    
    return commData;
  }
  
  public NestData createNestData()
  {
    NestData data = new NestData(nestName, team, centerX, centerY);
    data.score = calculateScore();
    return data;
  }
}
