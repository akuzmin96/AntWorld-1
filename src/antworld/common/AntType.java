package antworld.common;
/**
 *!!!!!!!!!! DO NOT MODIFY ANYTHING IN THIS CLASS !!!!!!!!!!<br>
 * This class is serialized across a network socket. Any modifications will
 * prevent the server from being able to read this class.<br><br>
 */

//new int[] {10,20,30,40,50,60,71,80,90,91};
public enum AntType
{
  ATTACK 
  {
    public FoodType[] getBirthFood() {return ATTACK_FOOD;}
    public int getMaxAttackDamage() {return super.getAttackDiceD4()*2;}
  },

  DEFENCE
  { public FoodType[] getBirthFood() {return DEFENCE_FOOD;}
    public int getMaxHealth() {return super.getMaxHealth()*2;}
  },

  MEDIC
  { public FoodType[] getBirthFood() {return MEDIC_FOOD;}
    public int getHealPointsPerWaterUnit() {return super.getHealPointsPerWaterUnit()*2;}
  },

  SPEED
  {
    public FoodType[] getBirthFood() {return SPEED_FOOD;}
    public int getBaseMovementTicksPerCell() {return super.getBaseMovementTicksPerCell()/2;}
  }, 
  
  VISION
  {
    public FoodType[] getBirthFood() {return VISION_FOOD;}
    public int getVisionRadius() {return super.getVisionRadius()*2;}
  },

  WORKER
  { public FoodType[] getBirthFood() {return WORKER_FOOD;}
    public int getCarryCapacity() {return super.getCarryCapacity()*2;}
  };

  
  
  //==========================================================================
  private static final FoodType[] ATTACK_FOOD = {FoodType.MEAT};
  private static final FoodType[] DEFENCE_FOOD = {FoodType.MEAT, FoodType.SEEDS};
  private static final FoodType[] MEDIC_FOOD = {FoodType.MEAT, FoodType.NECTAR};
  private static final FoodType[] SPEED_FOOD = {FoodType.NECTAR};
  private static final FoodType[] VISION_FOOD = {FoodType.NECTAR, FoodType.SEEDS};
  private static final FoodType[] WORKER_FOOD = {FoodType.SEEDS};


  public static final int TOTAL_FOOD_UNITS_TO_SPAWN = 10;

  public int getFoodUnitsToSpawn(FoodType type)
  {
    FoodType[] birthFood = getBirthFood();
    int units = TOTAL_FOOD_UNITS_TO_SPAWN/birthFood.length;
    for (FoodType food : birthFood)
    {
      if (type == food) return units;
    }
    return 0;
  }

  public abstract FoodType[] getBirthFood();
  public int getMaxHealth() {return 20;}

  /**
   * @return  The number of 4-sided dice rolled and summed to calculate the damage.
   * That is, the actual damage is the sum of n uniformly distributed random numbers from 1
   * through 4.
   */
  public int getAttackDiceD4() {return 2;}

  /**
   * Each turn an ant spends above ground, it has a chance of taking 1 point of damage.
   * It takes 2 turns for an unencumbered ant to move across flat grass and 4 turns if it is
   * carrying a full load. Therefore, ant that needs to travel from its nest to a food source
   * across the map and return with a full load, will take (not counting hills and obstacles)
   * 5000*2 + 5000*4 = 30,000 turns (about 50 minutes of wall-clock time).
   * For an ant at full health (20HP) to do be able to make such a trip and return with
   * half damage, the probability of damage per turn is 10*(1/30,000) = 0.0003333.
   * @return the probability that each ant takes 1 point of damage each time step it spends
   * outside the nest.
   */
  public double getAttritionDamageProbability() {return 0.0002;}
  public int getBaseMovementTicksPerCell() {return 2;}
  public int getUpHillMultiplier() {return 5;}
  public int getHalfEncumbranceMultiplier() {return 2;}
  
  public int getVisionRadius() {return 30;}
  public int getCarryCapacity() {return 25;}
  public static int getDeadAntFoodUnits() {return 5;}
  public static FoodType getDeadAntFoodType() {return FoodType.MEAT;}
  public int getHealPointsPerWaterUnit() {return 1;}
}
