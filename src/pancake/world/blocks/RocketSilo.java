package pancake.world.blocks;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.math.Mathf;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.*;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.gen.Iconc;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.ui.Bar;
import mindustry.ui.Cicon;
import mindustry.ui.Fonts;
import mindustry.world.Block;
import mindustry.world.consumers.ConsumeItemDynamic;
import pancake.Missile;

import static arc.Core.atlas;
import static mindustry.Vars.*;

/**
 * Брифинг
 *
 * Блок не выстреливает ракету в направлении, он как бы спавнит её над собой,
 * а дальше ракета сама управляет собой в соответствии с её поведением и
 * переданными параметрами цели от блока-шахты
 *
 * Типы ракет: ядерная, вакуумная, разрывная
 *
 * Если ты хочешь произвести нужную ракету, то ты должен подвести нужные для
 * её производства ресурсы в шахту и подключить электричество.
 * При нажатии на шахту, внизу выпадают иконки с возможными ракетами.
 * При нажатии на иконку начинается производство, которое занимает какое то время.
 * (С аниммацией типа "вжик-вжик")
 * (Механика такая же как у завода юнитов)
 * ТОЛЬКО ракета после производства не вылезает из блока шахты и не спавнится
 * как юнит - она встаёт в обратный стек (первый вошёл - первый вышел) и
 * визуально отображается в шахте.
 *
 * Блок может хранить в себе 3 ракеты разного типа, которые визуально
 * отображаются внутри шахты и проворачиваются при использовании последней ракеты.
 *
 * Так же при нажатии на шахту СВЕРХУ отображается кнопка запуска.
 * При нажатии на кнопку, игрока переводит в режим наведения (выходит из юнита) с орбитальным прицелом (шейдеры).
 * Игрок наводится на цель и нажимает кнопку < ПУСК > внизу экрана.
 * Для отмены наведения ПКМ/Esc (на телефонах: внизу справа кнопка с крестиком как при постройках)
 * Игрока возвращает в юнит на то же место (юнит всё это время просто стоит и, если его убивают,
 * игрока принудительно выкидывает из режима наведения)
 * Отображается для всех команд надпись < Обнаружен запуск ракеты >
 * Ракета берётся из стека и спавнится над шахтой с параметрами наведения
 *
 * Далее управление передаётся ракете.
 */
public class RocketSilo extends Block {
    /** Готовая ракета*/
    @Nullable
    public BulletType shootType;
    /** Вместимость предметов для конкретного типа ракеты */
    public int[] capacities;
    /** Максимальное кол-во хранимых боеголовок */
    public int siloCapacity = 3;
    /** Виды производимых боеголовок */
    public Seq<BulletPlan> plans = new Seq<>(siloCapacity);

    public RocketSilo(String name){
        super(name);
        configurable = true;
        rotate = true;

        config(Integer.class, (RocketSiloBuild tile, Integer i) -> {
            tile.currentPlan = i < 0 || i >= plans.size ? -1 : i;
            tile.progress = 0;
        });

        config(BulletType.class, (RocketSiloBuild tile, BulletType val) -> {
            tile.currentPlan = plans.indexOf(p -> p.bulletType == val);
            tile.progress = 0;
        });

        consumes.add(new ConsumeItemDynamic((RocketSiloBuild e) -> e.currentPlan != -1 ? plans.get(e.currentPlan).requirements : ItemStack.empty));
    }

    @Override
    public void init(){
        capacities = new int[content.items().size];
        for(BulletPlan plan : plans){
            for(ItemStack stack : plan.requirements){
                capacities[stack.item.id] = Math.max(capacities[stack.item.id], stack.amount * 2);
                itemCapacity = Math.max(itemCapacity, stack.amount * 2);
            }
        }

        super.init();
    }

    /** Шкалы прогресса */
    @Override
    public void setBars(){
        super.setBars();
        bars.add("progress", (RocketSiloBuild e) -> new Bar("bar.progress", Pal.ammo, e::fraction));
    }

    @Override
    public void load(){
        super.load();

        region = atlas.find(this.name);
    }

    /** Дата-класс с информацией о плане постройки */
    public static class BulletPlan{
        public BulletType bulletType;
        public ItemStack[] requirements;
        public float time;

        public BulletPlan(BulletType bulletType, float time, ItemStack[] requirements){
            this.bulletType = bulletType;
            this.time = time;
            this.requirements = requirements;
        }
    }

    public class RocketSiloBuild extends Building{
        /** Позиция плана */
        public int currentPlan = -1;
        public float progress, time, speedScl;

        /** Прогресс ДЕЛИТЬ на время производства ракеты */
        public float fraction(){
            return currentPlan == -1 ? 0 : progress / plans.get(currentPlan).time;
        }

        @Override
        public void updateTile(){
            if(consValid() && currentPlan != -1){
                time += edelta() * speedScl * state.rules.unitBuildSpeedMultiplier;
                progress += edelta() * state.rules.unitBuildSpeedMultiplier;
                speedScl = Mathf.lerpDelta(speedScl, 1f, 0.05f);
            }else{
                speedScl = Mathf.lerpDelta(speedScl, 0f, 0.05f);
            }

            if(currentPlan != -1 && shootType == null){
                BulletPlan plan = plans.get(currentPlan);

                if(progress >= plan.time && consValid()){
                    progress = 0f;

                    // Тут производится пуля и после стекуется
                    consume();
                }

                progress = Mathf.clamp(progress, 0, plan.time);
            }else{
                progress = 0f;
            }
        }

        /** Возвращает тип производимой сейчас пули */
        @Nullable
        public BulletType missile(){
            return currentPlan == - 1 ? null : plans.get(currentPlan).bulletType;
        }

        @Override
        public void draw(){
            /*Draw.rect(region, x, y);                     Отрисовка вжик-вжика или тип того
            Draw.rect(missile()., x, y, rotdeg());

            if(currentPlan != -1){
                BulletPlan plan = plans.get(currentPlan);
                Draw.draw(Layer.blockOver, () -> Drawf.construct(this, plan.bulletType, rotdeg() - 90f, progress / plan.time, speedScl, time));
            }

            Draw.z(Layer.blockOver);

            payRotation = rotdeg();
            drawPayload();

            Draw.z(Layer.blockOver + 0.1f);



            Draw.rect(topRegion, x, y);*/
        }

        /** Отрисовка ракеты */
        public void drawPayload(){
            /*if(sho != null){
                payload.set(x + payVector.x, y + payVector.y, payRotation);

                Draw.z(Layer.blockOver);
                payload.draw();
            }*/
        }

        //резко вклинивается Феликс
        protected void spawnMissle(Float destinationX, Float destinationY){}

        @Override
        public int getMaximumAccepted(Item item){
            return capacities[item.id];
        }
    }
}
