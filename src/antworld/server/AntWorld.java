package antworld.server;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import javax.swing.Timer;
import javax.swing.JFrame;
import antworld.common.AntData;
import antworld.common.CommData;
import antworld.common.Constants;
import antworld.common.FoodData;
import antworld.common.FoodType;
import antworld.common.LandType;
import antworld.common.NestData;
import antworld.common.NestNameEnum;
import antworld.common.TeamNameEnum;
import antworld.common.Util;
import antworld.server.GameObject.GameObjectType;
import antworld.server.Nest.NetworkStatus;
import antworld.renderer.DataViewer;
import antworld.renderer.Renderer;

public class AntWorld implements ActionListener
{
  public static Random random = Constants.random;
  public static final int FRAME_WIDTH = 1200;
  public static final int FRAME_HEIGHT = 700;

  public final boolean showGUI;

  public static final String title = "AntWorld Version: " + Constants.VERSION;
  private Renderer drawPanel;
  private Timer gameTimer;
  private long wallClock;
  private long lastRestoreTime;
  private static final long RESTORE_FREQUENCY = 1000 * 60 * 10;

  private static final int BLOCK_SIZE = 100;
  private final int worldWidth, worldHeight;
  private final int worldWidthInBlocks, worldHeightInBlocks;
  private Cell[][] world;
  private HashSet<AntData>[][] antBlocks;
  private HashSet<FoodData>[][] foodBlocks;
  private ArrayList<Nest> nestList = new ArrayList<>();
  private Server server;
  private DataViewer dataViewer;

  private ArrayList<FoodSpawnSite> foodSpawnList;
  private static int gameTick = 0;

  public AntWorld(boolean showGUI, String restorePoint)
  {
    this.showGUI = showGUI;
    System.out.println(title);

    JFrame window = null;
    if (showGUI)
    { drawPanel = new Renderer(this, title, FRAME_WIDTH, FRAME_HEIGHT);
      window = drawPanel.window;
    }

    //********************* Note On map replacement  **************************
    //The map must have at least a one pixel a boarder of water: LandType.WATER.getColor.
    BufferedImage map = Util.loadImage("AntWorld.png", window);
    worldWidth = map.getWidth();
    worldHeight = map.getHeight();


    worldWidthInBlocks  = (int) Math.ceil((double) worldWidth / BLOCK_SIZE);
    worldHeightInBlocks = (int) Math.ceil((double) worldHeight / BLOCK_SIZE);

    readAntWorld(map);

    if (restorePoint == null)
    {
      foodSpawnList = new ArrayList<FoodSpawnSite>();
      createFoodSpawnSite();
      //WorldRestore.writeFoodSpawnSites(foodSpawnList);
      //System.out.println("AntWorld.loadFoodSites()...."
      //  + foodSpawnList.size());
    }
    else
    {
      WorldData data = WorldRestore.loadRestorePoint(restorePoint);
      nestList = data.nestList;
      foodSpawnList = data.foodSpawnList;
      gameTick = data.gameTick;
    }

    System.out.println("World: " + worldWidth + " x " + worldHeight);

    for (Nest nest : nestList)
    {
      int x0 = nest.getCenterX();
      int y0 = nest.getCenterY();
      for (int x = x0 - Constants.NEST_RADIUS; x <= x0
        + Constants.NEST_RADIUS; x++)
      {
        for (int y = y0 - Constants.NEST_RADIUS; y <= y0
          + Constants.NEST_RADIUS; y++)
        {
          if (nest.isInNest(x, y))
          {
            world[x][y].setNest(nest);
          }
        }
      }
    }

    createHashMapsOfGameObjects();

    if (showGUI)
    {
      drawPanel.initWorld(world, worldWidth, worldHeight);
      drawPanel.repaint();
    }

    gameTimer = new Timer(Constants.TIME_STEP_MSEC, this);

    System.out.println("Done Initializing AntWorld");
    server = new Server(this, nestList);
    server.start();
    if (showGUI)
    {
      dataViewer = new DataViewer(nestList);
    }

    gameTimer.start();
  }

  public int getWorldWidthInBlocks() {return worldWidthInBlocks;}
  public int getWorldHeightInBlocks() {return worldHeightInBlocks;}
  public HashSet<FoodData>[][] getFoodBlocks() {return foodBlocks;}
  public ArrayList<FoodSpawnSite> getFoodSpawnList() {return foodSpawnList;}
  public ArrayList<Nest> getNestList() {return nestList;}

  private void createHashMapsOfGameObjects()
  {
    antBlocks  = new HashSet[worldWidthInBlocks][worldHeightInBlocks];
    foodBlocks = new HashSet[worldWidthInBlocks][worldHeightInBlocks];
    for (int i = 0; i < worldWidthInBlocks; ++i)
    {
      for (int j = 0; j < worldHeightInBlocks; ++j)
      {
        antBlocks[i][j] = new HashSet<AntData>();
        foodBlocks[i][j] = new HashSet<FoodData>();
      }
    }

    for (Nest myNest : nestList)
    {
      ArrayList<AntData> antList = myNest.getAntList();
      for (AntData ant : antList)
      {
        ant.id = Ant.getNewID();
      }
    }
  }

  public int getWorldWidth()
  {
    return worldWidth;
  }

  public int getWorldHeight()
  {
    return worldHeight;
  }

  public NestData[] createNestDataList()
  {
    NestData[] nestDataList = new NestData[nestList.size()];
    for (int i = 0; i < nestList.size(); i++)
    {
      Nest nest = nestList.get(i);
      nestDataList[i] = nest.createNestData();
    }
    return nestDataList;
  }

  public Nest getNest(NestNameEnum name)
  {
    return nestList.get(name.ordinal());
  }
  public Nest getNest(TeamNameEnum name)
  {
    for (Nest nest : nestList)
    {
      if (nest.team == name) return nest;
    }
    return null;
  }


  public long getWallClockAtLastUpdateStart()
  {
    return wallClock;
  }

  public static int getGameTick()
  {
    return gameTick;
  }


  /**
   * Uses the given map image to create the world including world size, nest
   * locations and all terrain.
   * @param map Must have the following properties:
   * <ol>
   *     <li>The map must have at least a one pixel a boarder of water:
   *              LandType.WATER.getColor</li>
   *
   *     <li>The map must have at least one pixel of 0x0 to define the nest.</li>
   *     <li>Each nest (pixel with 0x0 color) must be at least 2xNEST_RADIUS distant from each other nest.</li>
   *     <li>Map images must be resized using nearest neighbor NOT any type of interpolation or
   *            averaging which will create shades that are undefined.</li>
   *     <li>Map images must be saved in a lossless format (i.e. png).</li>
   *            </ol>
   */
  private void readAntWorld(BufferedImage map)
  {
    world = new Cell[worldWidth][worldHeight];
    for (int x = 0; x < worldWidth; x++)
    {
      for (int y = 0; y < worldHeight; y++)
      {
        int rgb = (map.getRGB(x, y) & 0x00FFFFFF);
        LandType landType;
        int height = 0;
        if (rgb == 0x0)
        {
          landType = LandType.NEST;
          NestNameEnum nestName = NestNameEnum.values()[Nest.getNextID()];
          nestList.add(new Nest(nestName, x, y));
        }
        else if (rgb == 0xF0E68C)
        {
          landType = LandType.NEST;
        }
        else if (rgb == 0x1E90FF)
        {
          landType = LandType.WATER;
        }
        else
        { landType = LandType.GRASS;
          height=LandType.getMapHeight(rgb);
        }
        // System.out.println("("+x+","+y+") rgb="+rgb +
        // ", landType="+landType
        // +" height="+height);
        world[x][y] = new Cell(landType, height, x, y);
      }
    }

    // for (Nest nest : nestList)
    // {
    // int x0 = nest.getCenterX();
    // int y0 = nest.getCenterY();
    // for (int x = x0 - Constants.NEST_RADIUS; x <= x0 +
    // Constants.NEST_RADIUS; x++)
    // {
    // for (int y = y0 - Constants.NEST_RADIUS; y <= y0 +
    // Constants.NEST_RADIUS; y++)
    // {
    // if (nest.isInNest(x, y))
    // {
    // world[x][y].setNest(nest);
    // }
    // }
    // }
    // }
  }

  public Cell getCell(int x, int y)
  {

    if (x < 0 || y < 0 || x >= worldWidth || y >= worldHeight)
    {
      // System.out.println("AntWorld().getCell(" + x + ", " + y +
      // ") worldWidth=" + worldWidth + ", worldHeight="
      // + worldHeight);
      return null;
    }
    return world[x][y];
  }

  public Nest getNest(int x, int y)
  {

    if (x < 0 || y < 0 || x >= worldWidth || y >= worldHeight)
    {
      // System.out.println("AntWorld().getCell(" + x + ", " + y +
      // ") worldWidth=" + worldWidth + ", worldHeight="
      // + worldHeight);
      return null;
    }
    return world[x][y].getNest();
  }

  public void addAnt(AntData ant)
  {
    if (ant.underground)
    { return; }
    int x = ant.gridX;
    int y = ant.gridY;

    world[x][y].setAnt(ant);
    antBlocks[x / BLOCK_SIZE][y / BLOCK_SIZE].add(ant);

    if (drawPanel != null) drawPanel.drawCell(world[x][y]);
  }

  public void addFood(FoodSpawnSite foodSpawnSite, FoodData food)
  {
    int x = food.gridX;
    int y = food.gridY;

    world[x][y].setFood(foodSpawnSite, food);
    foodBlocks[x / BLOCK_SIZE][y / BLOCK_SIZE].add(food);

    if (drawPanel != null) drawPanel.drawCell(world[x][y]);
  }

  public void removeAnt(AntData ant)
  {
    if (ant == null)
    { return; }
    int x = ant.gridX;
    int y = ant.gridY;

    world[x][y].setAnt(null);
    antBlocks[x / BLOCK_SIZE][y / BLOCK_SIZE].remove(ant);
    if (drawPanel != null) drawPanel.drawCell(world[x][y]);
  }

  public void removeFood(FoodData food)
  {
    if (food == null)
    { return; }
    int x = food.gridX;
    int y = food.gridY;

    world[x][y].setFood(null, null);
    foodBlocks[x / BLOCK_SIZE][y / BLOCK_SIZE].remove(food);
    if (drawPanel != null) drawPanel.drawCell(world[x][y]);
  }

  public void removeGameObj(GameObject obj)
  {
    if (obj == null)
    { return; }
    if (obj.type == GameObjectType.ANT)
    { removeAnt(obj.ant); }

    else
    { removeFood(obj.food); }
  }

  public void moveAnt(AntData ant, Cell from, Cell to)
  {
    from.setAnt(null);
    to.setAnt(ant);

    ant.gridX = to.getLocationX();
    ant.gridY = to.getLocationY();

    if (drawPanel != null)
    {  drawPanel.drawCell(from);
       drawPanel.drawCell(to);
    }
    antBlocks[from.getLocationX() / BLOCK_SIZE][from.getLocationY()
      / BLOCK_SIZE].remove(ant);
    antBlocks[to.getLocationX() / BLOCK_SIZE][to.getLocationY()
      / BLOCK_SIZE].add(ant);
  }

  public void appendAntsInProximity(AntData myAnt, HashSet<AntData> antSet)
  {
    double x = myAnt.gridX;
    double y = myAnt.gridY;
    double radius = myAnt.antType.getVisionRadius();
    NestNameEnum nestExclude = myAnt.nestName;

    for (int i = (int) Math.max(
      Math.floor(x / BLOCK_SIZE - radius / BLOCK_SIZE), 0); i <= Math
      .min(Math.ceil(x / BLOCK_SIZE + radius / BLOCK_SIZE),
        antBlocks.length - 1); ++i)
    {
      for (int j = (int) Math.max(
        Math.floor(y / BLOCK_SIZE - radius / BLOCK_SIZE), 0); j <= Math
        .min(Math.ceil(y / BLOCK_SIZE + radius / BLOCK_SIZE),
          antBlocks[i].length - 1); ++j)
      {
        for (AntData ant : antBlocks[i][j])
        {
          if (ant.nestName == nestExclude)
          { continue; }
          if (Util.manhattanDistance((int) x, (int) y, ant.gridX,
            ant.gridY) <= radius)
          { antSet.add(ant); }
        }
      }
    }
  }

  // public boolean isAntInProximity(double x, double y, double radius)
  // {
  // for (int i = (int) Math.max(Math.floor(x / BLOCK_SIZE - radius /
  // BLOCK_SIZE), 0); i <= Math.min(
  // Math.ceil(x / BLOCK_SIZE + radius / BLOCK_SIZE), antBlocks.length - 1);
  // ++i)
  // {
  // for (int j = (int) Math.max(Math.floor(y / BLOCK_SIZE - radius /
  // BLOCK_SIZE), 0); j <= Math.min(
  // Math.ceil(y / BLOCK_SIZE + radius / BLOCK_SIZE), antBlocks[i].length -
  // 1); ++j)
  // {
  // for (AntData ant : antBlocks[i][j])
  // {
  //
  // if (Util.manhattanDistance((int) x, (int) y, ant.gridX, ant.gridY) <=
  // radius) return true;
  // }
  // }
  // }
  // return false;
  // }

  public void appendFoodInProximity(AntData myAnt, HashSet<FoodData> foodSet)
  {
    double x = myAnt.gridX;
    double y = myAnt.gridY;
    double radius = myAnt.antType.getVisionRadius();

    for (int i = (int) Math.max(Math.floor(x / BLOCK_SIZE - radius / BLOCK_SIZE), 0); i <= Math.min(
      Math.ceil(x / BLOCK_SIZE + radius / BLOCK_SIZE), foodBlocks.length - 1); ++i)
    {
      for (int j = (int) Math.max(Math.floor(y / BLOCK_SIZE - radius / BLOCK_SIZE), 0); j <= Math.min(
        Math.ceil(y / BLOCK_SIZE + radius / BLOCK_SIZE), foodBlocks[i].length - 1); ++j)
      {
        for (FoodData food : foodBlocks[i][j])
        {
          if (food.count <= 0)
          {
            System.out.println("AntWorld.appendFoodInProximity() ***ERROR*** Empty food");
          }

          else if (Util.manhattanDistance((int) x, (int) y, food.gridX, food.gridY) <= radius)
          {
            foodSet.add(food);
          }
        }
      }
    }
  }

  public void actionPerformed(ActionEvent e)
  {
    wallClock = System.currentTimeMillis();
    gameTick++;

    if (random.nextDouble() < 0.01)
    {
      int foodSiteIdx = random.nextInt(foodSpawnList.size());
      foodSpawnList.get(foodSiteIdx).spawn(this);
    }

    // System.out.println("AntWorld:: Timer " + gameTick);
    for (Nest myNest : nestList)
    { myNest.updateRemoveDeadAntsFromAntList(); }

    for (Nest myNest : nestList)
    {
      if (myNest.team == TeamNameEnum.NEARLY_BRAINLESS_BOTS)
      {
        CommData commData = NearlyBrainlessBots.chooseActionsOfAllAnts(
          world, myNest);
        myNest.updateReceive(this, commData);
        continue;
      }

      if (wallClock > myNest.getTimeOfLastMessageFromClient()
        + Server.TIMEOUT_MAX_MSEC_BETWEEN_RECV)
      {
        server.closeClient(myNest.nestName);
        myNest.sendAllAntsUnderground(this);
      }

      if (myNest.getNetworkStatus() == NetworkStatus.UNDERGROUND)
      { continue; }

      ServerToClientConnection client = server.getClient(myNest.nestName);

      if (client == null)
      { continue; }

      if (myNest.getNetworkStatus() != NetworkStatus.CONNECTED)
      {
        server.closeClient(myNest.nestName);
        continue;
      }

      synchronized (client)
      {
        CommData commData = client.getCommData();
        myNest.updateReceive(this, commData);
      }
    }

    for (Nest myNest : nestList)
    { myNest.updateRemoveDeadAntsFromWorld(this); }

    for (Nest myNest : nestList)
    {
      if (myNest.team == TeamNameEnum.NEARLY_BRAINLESS_BOTS)
      { continue; }

      ServerToClientConnection client = server.getClient(myNest.nestName);
      if (client == null)
      { continue; }

      synchronized (client)
      {
        CommData commData = client.getCommData();
        CommData outCommData = myNest.updateSendPacket(this, commData);

        client.send(outCommData);
      }
    }

    if (drawPanel != null)
    {  drawPanel.update();
      dataViewer.update(nestList);
    }

    if (wallClock >= lastRestoreTime + RESTORE_FREQUENCY)
    {
      //WorldRestore.saveRestorePoint(world, nestList, foodSpawnList, gameTick);
      lastRestoreTime = wallClock;
    }
  }

  private void createFoodSpawnSite()
  {
    int totalSitesToSpawn = 3 + random.nextInt(3);
    int xRange = worldWidth/totalSitesToSpawn;
    while (totalSitesToSpawn > 0)
    {
      int spawnX = random.nextInt(xRange);
      spawnX = spawnX + (totalSitesToSpawn-1)*xRange;
      int spawnY = random.nextInt(worldHeight);

      if (world[spawnX][spawnY].getLandType() == LandType.GRASS)
      {
        FoodType foodType;
        if (foodSpawnList.size() == 0) foodType=FoodType.MEAT;
        else if (foodSpawnList.size() == 1) foodType=FoodType.SEEDS;
        else if (foodSpawnList.size() == 2) foodType=FoodType.NECTAR;
        else foodType = FoodType.getRandomFood();
        foodSpawnList.add(new FoodSpawnSite(foodType, spawnX, spawnY, nestList.size()));
        //System.out.println("FoodSpawnSite: [ " + spawnX + ", " + spawnY + "] " + foodType);
        totalSitesToSpawn--;
      }
    }
  }

  /*
  private void loadFoodSites(BufferedImage map)
  {
    foodSpawnList = new ArrayList<FoodSpawnSite>();
    for (int x = 0; x < worldWidth; x++)
    {
      for (int y = 0; y < worldHeight; y++)
      {
         int rgb = (map.getRGB(x, y) & 0x00FFFFFF);
         FoodType foodType = FoodType.identifyTypeByColor(rgb);

         if (foodType != null)
         {
           foodSpawnList.add(new FoodSpawnSite(foodType, x, y, nestList.size()));
         }
      }
    }
  }
  // // System.out.println("AntWorld.loadFoodSites()...." +
  // foodSpawnList.size());
  // // System.out.println("AntWorld.spawnInitialFood()");
  // // for (FoodSpawnSite spawnSite : foodSpawnList)
  // // {
  // // spawnSite.spawn(this);
  // // }
  // }

*/

  public static void main(String[] args)
  {
    boolean showGUI = true;
    if (args != null && args.length > 0)
    {
      for (String field : args)
      {
        if (field.equals("-nogui"))  showGUI = false;
      }
    }
    new AntWorld(showGUI, null);
  }

}
