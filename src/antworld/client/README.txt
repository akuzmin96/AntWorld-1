CS351: Ant World AI
Anton Kuzmin and Joshua Donckels

> Summary:
  This program is an artificial intelligence that controls a set of ants on a map.
  The main objective is to survive and get the most amount of ants and resources

> Run program as java -jar JoshAntonAntAI

> Credits:
  Code for the directional bits: Joel Castellanos

> Contents:
  .zip file: contains the source folder with all of the .java files, a README, the
	   executable .jar file

  .jar file: contains the compiled .class files and the source .java files

> Main Class: JoshAntonAntAI

> Other comments:
 - Exploring in pulses: Ants go out, rotate, and the come back (ensures that they cover everything around the base)
 - Attacking: Ants attack if they outnumber an enemy ant, if the enemy ant is carrying food, or if the enemy attacked
 - Water: There are 10 ants that gather water until the water supply is 5000 units
 - Food: As soon as an ant sees food, other ants come to help gather all of it and return to the base
 - Healing: Ant goes back to base if it is hurt
 - Getting stuck: Ants get unstuck if they are clumped or if they hit water