package pancake;

import mindustry.graphics.*;
import mindustry.gen.*;
import mindustry.entities.bullet.MissileBulletType;

/**
 * Стоит не забывать, что у каждой ракеты два региона, передний и задний.
 * А это значит, что надо <b>2</> текстуры на ракету
 */
public class Missile extends /* летит летит ракета и тут её Сегмент уничтожает*/ MissileBulletType {
    public String name; // Так как у пуль нет названия, то мы его тут ставим, хотя хз даже нужно ли

    public Missile(float speed, float damage, String bulletSprite){
        super(speed, damage, bulletSprite);
        name = bulletSprite; // Айяйяй
        backColor = Pal.missileYellowBack;
        frontColor = Pal.missileYellow;
        homingPower = 0.08f;
        shrinkY = 0f;
        width = 8f;
        height = 8f;
        hitSound = Sounds.explosion;
        trailChance = 0.2f;
        lifetime = 49f;
    }
}
