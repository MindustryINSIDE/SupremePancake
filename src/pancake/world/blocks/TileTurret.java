package pancake.world.blocks;

import arc.func.Floatf;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.ui.layout.Table;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.graphics.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.production.Drill;

import static arc.Core.*; // фокусы
import static mindustry.Vars.*;

/**
 * <b>Небольшой брифинг что бы не ждать друг друга</b>
 *
 * турель по умолчанию isActivated = false
 * isBreakable = false и т.д.
 * короче неуязвима и мимикрирует под тайл на котором находится
 *
 * После того как любой вражеский для неё юнит входит в зону поражения
 * турель переходит в режим isActivated и становится "обычным" блоком
 * Если isActivated, она стреляет, если есть патроны в барабане и вращает стволы
 * Так же она должны "разгоняться" так же как разгоняется, например, воздушный бур
 * т.е. разгоняется И раскрутка, И темп стрельбы
 * После израсходования барабана турель перестаёт стрелять и раскрутка медленно останавливается.
 */
public class TileTurret extends Turret {
    public BulletType shootType;
    /** Время раскрутки стволов */
    public float warmupTime = 90f;
    public float warmupSpeed = 0.02F;
    /** Время активации турели */
    public float deployTime = 60f;
    public float deploySpeed;
    /** Кол-во стволов по окружности турели */
    public int barrelsAmount = 4;
    public TextureRegion shadowRegion;
    //public TextureRegion outlineFrameRegion;

    public TileTurret(String name) {
        super(name);
        destructible = false;
        solid = false;
        solidifes = true;
        breakable = false;
        alwaysReplace = false;
        targetAir = false;
        targetGround = true;
        outlineIcon = true;
        hasShadow = false;
        acceptCoolant = false;
        buildType = TileTurretBuild::new;
    }

    @Override
    public void load() {
        super.load();
        region = atlas.find(this.name);
        shadowRegion = atlas.find(this.name + "-shadow");
        //outlineFrameRegion = atlas.find(this.name + "-outline-frame");
    }

    @Override
    public void drawEnvironmentLight(Tile tile){}

    @Override
    public int minimapColor(Tile tile) {
        return !tile.<TileTurretBuild>bc().activated ? tile.floor().mapColor.rgba() : 0;
    }

    public static class TurretBarrel {
        public float recoil, heat = -1;
        public float baseAngle;

        TurretBarrel(float baseAngle) {
            this.baseAngle = baseAngle;
        }
    }

    public class TileTurretBuild extends TurretBuild {
        public boolean activated = false;
        public Seq<TurretBarrel> barrels = new Seq<>();
        public float deployProgress, warmup = 0;

        public void activate() {
            activated = true;
        }

        //TODO: Разобраться почему всё-равно рисуется пустая рамка
        @Override
        public void display(Table table) {
            if (activated) super.display(table);
        }

        @Override
        public void created() {
            for (int i = 0; i < barrelsAmount; i++) {
                barrels.add(new TurretBarrel(360f / barrelsAmount * i));
            }
            totalAmmo = maxAmmo;

            super.created();
        }

        @Override
        public void drawTeam() {
            if (activated) super.drawTeam();
        }

        @Override
        public void drawLight() {
            if (activated) {
                Drawf.light(x, y, 24f, Color.coral, 0.2f);
            }
        }

        @Override
        public void draw() {
            if (activated) {
                Draw.z(Layer.block - 1);
                Draw.alpha(deployProgress / deployTime);
                Draw.rect(shadowRegion, x, y); // костыль с динамическими тенями
                Floatf<Float> shadowShift = a -> (a - size * 1.5f) * deployProgress / deployTime;
                Drawf.shadow(floor().region, shadowShift.get(x), shadowShift.get(y));

                Draw.z(Layer.turret);
                Draw.color();
                barrels.each(this::drawBarrel);
                if(heatRegion != atlas.find("error")) barrels.each(this::drawBarrelHeat);

                Draw.z(Layer.turret + 0.02f);
                Draw.rect(floor().region, x, y);
                //Draw.rect(outlineFrameRegion, x, y);
                Draw.color(Color.coral);
                Draw.alpha(deployProgress / deployTime);
                Draw.rect(atlas.find("block-middle"), x, y);

                Draw.reset();
            }
        }

        protected void drawBarrel(TurretBarrel barrel) {
            tr2.trns(barrel.baseAngle + rotation, -barrel.recoil + size * tilesize / 2f);
            Drawf.shadow(region, x + tr2.x - (size / 2f), y + tr2.y - (size / 2f), barrel.baseAngle + rotation - 90);
            Draw.rect(region, x + tr2.x, y + tr2.y, barrel.baseAngle + rotation - 90);
        }

        protected void drawBarrelHeat(TurretBarrel barrel) {
            if(barrel.heat <= 0.00001f) return;

            Draw.color(heatColor, barrel.heat);
            Draw.blend(Blending.additive);
            Draw.rect(heatRegion, tile.x + tr2.x, tile.y + tr2.y, barrel.baseAngle + rotation);
            Draw.blend();
            Draw.color();
        }

        @Override
        public void updateTile() {
            if (!validateTarget()) target = null;
            if (validateTarget() && !activated) {
                activate();
            }

            unit.health(health);
            unit.rotation(rotation);
            unit.team(team);
            unit.set(x, y); // 112.1

            if (logicControlTime > 0) {
                logicControlTime -= Time.delta;
            }

            // Каждый ствол имеет разную отдачу и нагрев во времени
            barrels.each(b -> {
                b.recoil = Mathf.lerpDelta(b.recoil, 0f, restitution);
                b.heat = Mathf.lerpDelta(b.heat, 0f, cooldown);
            });

            if (hasAmmo()) {
                if (timer(timerTarget, targetInterval)) {
                    findTarget();
                }

                if (activated) {
                    //float delay *= this.efficiency();
                    //warmup = Mathf.lerpDelta(this.warmup, delay, warmupSpeed);
                    if (validateTarget()) {
                        boolean canShoot = true;

                        if (isControlled()) { //player behavior
                            targetPos.set(unit.aimX(), unit.aimY());
                            canShoot = unit.isShooting();
                        } else if (logicControlled()) { //logic behavior
                            canShoot = logicShooting;
                        } else { //default AI behavior
                            targetPosition(target);

                            if (Float.isNaN(rotation)) {
                                rotation = 0;
                            }
                        }

                        float targetRot = angleTo(targetPos);

                        if (shouldTurn()) {
                            turnToTarget(targetRot);
                        }

                        if (Angles.angleDist(rotation, targetRot) < shootCone && canShoot) {
                            updateShooting();
                        }
                    }
                }
            }

            if (acceptCoolant) {
                updateCooling();
            }
        }

        @Override
        public boolean checkSolid(){
            return activated;
        }

        @Override
        public BulletType useAmmo(){
            //debug
            //totalAmmo -= ammoPerShot;
            //totalAmmo = Math.max(totalAmmo, 0);
            //Time.run(reloadTime / 2f, this::ejectEffects);
            ejectEffects(); // 111 -> 112.1
            return shootType;
        }

        @Override
        public boolean hasAmmo(){
            return totalAmmo >= 1;
        }

        @Override
        public BulletType peekAmmo() {
            return shootType;
        }

        @Override
        protected void shoot(BulletType type){
            barrels.each(b -> {
                b.recoil = recoilAmount;
                b.heat = 1f;
            });

            //when burst spacing is enabled, use the burst pattern
            if(burstSpacing > 0.0001f){
                for(int i = 0; i < shots; i++){
                    Time.run(burstSpacing * i, () -> {
                        if(!isValid() || !hasAmmo()) return;

                        recoil = recoilAmount;

                        tr.trns(rotation, size * tilesize / 2f, Mathf.range(xRand));
                        bullet(type, rotation + Mathf.range(inaccuracy));
                        effects();
                        useAmmo();
                    });
                }

            }else{
                //otherwise, use the normal shot pattern(s)

                if(alternate){
                    //float i = (shotCounter % shots) - shots/2f + (((shots+1)%2) / 2f);
                    float i = (shotCounter % shots) - (shots-1)/2f; // 111 -> 112.1

                    tr.trns(rotation - 90, spread * i + Mathf.range(xRand), size * tilesize / 2f);
                    ////bullet(type, rotation + Mathf.range(inaccuracy));
                }else{
                    tr.trns(rotation, size * tilesize / 2f, Mathf.range(xRand));

                    for(int i = 0; i < shots; i++){
                        ////bullet(type, rotation + Mathf.range(inaccuracy + type.inaccuracy) + (i - (int)(shots / 2f)) * spread);
                    }
                }

                shotCounter++;

                effects();
                useAmmo();
            }
        }

        @Override
        protected void effects(){
            Effect fshootEffect = shootEffect == Fx.none ? peekAmmo().shootEffect : shootEffect;
            Effect fsmokeEffect = smokeEffect == Fx.none ? peekAmmo().smokeEffect : smokeEffect;

            barrels.each(b -> {
                tr.trns(b.baseAngle + rotation, size * tilesize / 2f + 3 /* корректировка позиции конца ствола */);
                fshootEffect.layer(Layer.turret + 0.01f).at(x + tr.x, y + tr.y, b.baseAngle + rotation);
                fsmokeEffect.layer(Layer.turret + 0.01f).at(x + tr.x, y + tr.y, b.baseAngle + rotation);
            });
            shootSound.at(x, y, Mathf.random(0.9f, 1.1f));

            if(shootShake > 0){
                Effect.shake(shootShake, shootShake, this);
            }

            barrels.each(b -> {
                b.recoil = recoilAmount;
            });
        }

        @Override
        protected void ejectEffects(){
            if(!isValid()) return;

            barrels.each(b -> {
                ammoUseEffect.layer(Layer.turret + 0.01f).at(
                        x - Angles.trnsx(b.baseAngle + rotation, ammoEjectBack - size * tilesize / 2f),
                        y - Angles.trnsy(b.baseAngle + rotation, ammoEjectBack - size * tilesize / 2f),
                        rotation + b.baseAngle
                );
            });
        }

        @Override
        public byte version(){
            return 1;
        }
    }
}
