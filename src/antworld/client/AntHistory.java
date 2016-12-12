package antworld.client;

import antworld.common.AntData;

/**
 * Created by Anton on 12/11/2016.
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
  
  public int getAntID()
  {
    return antID;
  }
  
  public void setEnemyAnt(AntData enemy)
  {
    enemyAnt = enemy;
  }
  
  public AntData getEnemyAnt()
  {
    return enemyAnt;
  }
}
