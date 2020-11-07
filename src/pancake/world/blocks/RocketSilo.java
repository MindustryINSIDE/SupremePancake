package pancake.world.blocks;

import arc.Core;
import arc.func.Boolf;
import arc.func.Cons;
import arc.func.Prov;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.math.Mathf;
import arc.math.geom.Position;
import arc.math.geom.Vec2;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.ImageButton;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.Queue;
import arc.struct.Seq;
import arc.util.*;
import mindustry.content.Bullets;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.*;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.ui.Bar;
import mindustry.ui.Cicon;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.blocks.ItemSelection;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.consumers.ConsumeItemDynamic;
import pancake.Missile;

import javax.sound.midi.MidiChannel;
import javax.xml.namespace.QName;

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
    /** Готовая ракета */
    @Nullable
    public BulletType shootType;
    /** Вместимость предметов для конкретного типа ракеты */
    public int[] capacities;
    /** Виды производимых боеголовок */
    public Seq<BulletPlan> plans = new Seq<>();
    /** Минимальный радиус стрельбы */
    public float minRange;

    /** Для стрельбы, зависит от игрока */
    protected Vec2 tr = new Vec2();

    public RocketSilo(String name){
        super(name);
        configurable = true;
        rotate = true;
        minRange = 10f;
        hasPower = true;

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
        /** Позиция скролла списочка */
        private float scrollPos = 0f;
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

            moveUpMissile();

            if(currentPlan != -1 && shootType != null){
                BulletPlan plan = plans.get(currentPlan);

                if(progress >= plan.time && consValid()){
                    progress = 0f;

                    shootType = missile();

                    consume();
                }

                progress = Mathf.clamp(progress, 0, plan.time);
            }else{
                progress = 0f;
            }
        }

        /** Отрисовка поднимающиейся из шахты ракеты */
        public void moveUpMissile(){
            if(shootType == null) return;

            // TODO сделать блять отрисовку
        }

        /** Возвращает тип производимой сейчас пули */
        @Nullable
        public BulletType missile(){
            return currentPlan == -1 ? null : plans.get(currentPlan).bulletType;
        }

        /** Интерфейс с выбором */
        @Override
        public void buildConfiguration(Table table){
            if(shootType == null){
                Seq<Missile> types = plans.map(p -> (Missile) p.bulletType);

                buildTable(
                        table, types,
                        () -> currentPlan == -1 ? null : (Missile) plans.get(currentPlan).bulletType,
                        m -> configure(plans.indexOf(u -> u.bulletType == m))
                );
            }else{ // Делаем меню управления, ибо ракета готова
                assert true; //
            }
        }

        /**
         * Рисовалка выборщика
         * (Лучше переместить в другое место)
         */
        public <T extends BulletType> void buildTable(Table table, Seq<T> items,  Prov<T> holder, Cons<T> consumer){
            ButtonGroup<ImageButton> group = new ButtonGroup<>();
            group.setMinCheckCount(0);
            Table cont = new Table();
            cont.defaults().size(40);

            int i = 0;

            for(T item : items) {
                ImageButton button = cont.button(Tex.whiteui, Styles.clearToggleTransi, 24, () -> {
                    control.input.frag.config.hideConfig();
                }).group(group).get();
                button.changed(() -> consumer.get(button.isChecked() ? item : null));
                button.getStyle().imageUp = new TextureRegionDrawable(((Missile)item).frontRegion);
                button.update(() -> button.setChecked(holder.get() == item));

                if (i++ % 4 == 3) {
                    cont.row();
                }
            }

            if(i % 4 != 0){
                int remaining = 4 - (i % 4);
                for(int j = 0; j < remaining; j++){
                    cont.image(Styles.black6);
                }
            }

            ScrollPane pane = new ScrollPane(cont, Styles.smallPane);
            pane.setScrollingDisabled(true, false);
            pane.setScrollYForce(scrollPos);
            pane.update(() -> {
                scrollPos = pane.getScrollY();
            });

            pane.setOverscroll(false, false);
            table.add(pane).maxHeight(Scl.scl(40 * 5));
        }

        /** Отрисовка самой постройки, а так же ракеты внутри */
        @Override
        public void draw(){
            Draw.rect(region, x, y);

            if(currentPlan != -1){
                // Вжик-вжик
                BulletPlan plan = plans.get(currentPlan);
                Draw.draw(Layer.blockOver, () -> {
                    Drawf.construct(this, ((Missile) plan.bulletType).frontRegion, rotdeg() - 90f,
                            progress / plan.time, speedScl, time);
                });
            }

            Draw.z(Layer.blockOver);

            drawMissile();

            Draw.z(Layer.blockOver + 0.1f);
        }

        /** Отрисовка ракеты собственно */
        public void drawMissile(){
            if(missile() != null){
                Draw.z(Layer.blockOver); // чё эта? Это высота (3D) для корректного пересечения объектов отрисовки
                // Движок то трёхмерный  аааааааааааааа, я прост не думал что именно тут про это
                // Core.app,post(() -> Log.info("СделОй эффекты шоль")); // run
                Draw.rect(((Missile) missile()).frontRegion, x, y);
            }
        }


        /* public boolean hasAmmo() {
            return стек_ракет.size > 0;
        }

        public void useAmmo() {
            Missle ракета = стек_ракет.first();
            стек_ракет.first().pop(); // И далее сдвиг стека или как он там работкает хуй знает
            spawnMissile(destinationX, destinationY);
            ejectEffects(); // Если таковые вообще будут со стороны самой Турели (например, откидывание крышки, (которой нет))
        } */

        /** Спавн и в то же время погибель пули */
        protected void spawnMissile(float destX, float destY){
            Bullet bullet = shootType.create(null, team, destX, destY, 0f);

            bullet.draw(); // А это что такое

            // . . . Эфекты тут

            bullet.remove();

            Timer.schedule(() -> {
                Call.infoToast("ВНИМАЕНИЕ, НА ВАС ЛЕТИТ САС", 1337);
            }, 0, 3, 3);

            Call.createBullet(shootType, team, destX, destY, 0f, shootType.damage, 0f, 0f);

            shootType = null; // Говорим остановитЯЗЬ
        }

        /** Условие потреблядства */
        @Override
        public boolean shouldConsume(){
            if(currentPlan == -1) return false;
            return enabled && shootType == null;
        }

        /** Принятие ресов на постройку */
        @Override
        public boolean acceptItem(Building source, Item item){
            return currentPlan != -1 && items.get(item) < getMaximumAccepted(item) &&
                   Structs.contains(plans.get(currentPlan).requirements, stack -> stack.item == item);
        }

        @Override
        public int getMaximumAccepted(Item item){
            return capacities[item.id];
        }
    }
}
