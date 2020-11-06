package pancake;

import mindustry.graphics.*;
import mindustry.gen.*;
import mindustry.entities.bullet.MissileBulletType;

/**
 *
 */
public class Missile extends /* летит летит ракета и тут её Сегмент уничтожает*/ MissileBulletType {
    public String name;

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

    public Missile(float speed, float damage){
        this(speed, damage, "missile");
    }
}
