package pancake;

import arc.struct.Seq;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.ContinuousLaserBulletType;
import mindustry.gen.*;
import arc.util.*;
import pancake.world.blocks.*;
import mindustry.type.*;
import mindustry.content.*;
import mindustry.world.*;
import mindustry.mod.*;

import static mindustry.type.ItemStack.with;

public class SupremePancake extends Mod{

    // Blocks
    public static Block
    tileTurret;//, rocketSilo;

    // Entities
//    public static BulletType
//    nuclearMissile, hydrogenMissile, explosiveMissile;


    public SupremePancake(){
        Log.info("SupremePancake constructor loaded.");
    }


    // Загрузка контента
    @Override
    public void loadContent(){
        Log.info("[green][[SupremePancake][] loading...");
/*
        // Это для инициализации ракеты как пули
        explosiveMissile = new Missile(0, 0, "error"){{
            width = 8f;
            height = 8f;
            shrinkY = 0f;
            drag = -0.01f;
            splashDamageRadius = 30f;
            splashDamage = 30f;
            ammoMultiplier = 4f;
            hitEffect = Fx.blastExplosion;
            despawnEffect = Fx.blastExplosion;

            status = StatusEffects.blasted;
            statusDuration = 60f;
        }};

        // Я думаю сами ракеты лучше в конструкторе шахты задавать, так же как снаряды задаются у турелей (чекни Blocks.java)

        rocketSilo = new RocketSilo("rocket-silo"){{
            requirements(Category.effect, /*-> временно*//* with(Items.copper, 35));
            plans = Seq.with(
                    new BulletPlan(nuclearMissile, 60f * 15, with(Items.silicon, 10, Items.lead, 10)),
                    new BulletPlan(hydrogenMissile, 60f * 12, with(Items.silicon, 10, Items.coal, 20)),
                    new BulletPlan(explosiveMissile, 60f * 40, with(Items.silicon, 30, Items.lead, 20, Items.titanium, 20))
            ); // Тут задаются все варианты ракет
            size = 6;
        }};
*/
        tileTurret = new TileTurret("tile-turret"){{
            requirements(Category.turret, with(Items.copper, 35));
            shootType  = Bullets.standardCopper;
            shootSound = Sounds.shoot;
            range = 80.0f;
            rotateSpeed = 5.0f;
            maxAmmo = 30;
            chargeTime = 90f;
            deployTime = 60f;
            barrelsAmount = 3;
            reloadTime = 3f;
            range = 100;
            ammoUseEffect = Fx.casing1;
            health = 150;
            size = 1;
            description = "a";
        }};
    }
}
