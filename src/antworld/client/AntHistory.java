package antworld.client;

import antworld.common.AntData;

/**
 * This class is used to help my ants focus on one target when attacking an enemy ant.
 *
 * @author Anton Kuzmin
 */
public class AntHistory
{
  private int antID;
  private AntData enemyAnt;
  
  public AntHistory(int antID)
  {
    this.antID = antID;
    enemyAnt = null;
  }
  
  /**
   * Get the current ant id
   * @return ant id
   */
  public int getAntID()
  {
    return antID;
  }
  
  /**
   * Set the enemy ant
   * @param enemy ant
   */
  public void setEnemyAnt(AntData enemy)
  {
    enemyAnt = enemy;
  }
  
  /**
   * Return the enemy ant
   * @return enemy ant
   */
  public AntData getEnemyAnt()
  {
    return enemyAnt;
  }
}
