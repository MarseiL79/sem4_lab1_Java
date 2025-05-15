// Habitat.java
import java.awt.Graphics;
import java.awt.Dimension;
import java.io.Serializable;
import java.util.*;

public class Habitat implements Serializable {
    private static final long serialVersionUID = 1L;

    public static Dimension areaSize;

    int n1;         // период взрослых (мс)
    double p1;      // вероятность взрослых
    int n2;         // период птенцов (мс)
    public int kPercent;   // % птенцов к взрослым

    long adultLifetime;
    long chickLifetime;

    private long simulationTime;
    private long lastAdultTime;
    private long lastChickTime;

    private LinkedList<Bird> birds;
    private TreeSet<Integer> idSet;
    private HashMap<Integer,Long> birthTimeMap;

    private boolean globalAdultActive = true;
    private boolean globalChickActive = true;
    private double globalAdultAngle = Math.PI/4;
    private double globalChickAngle = Math.PI/4;

    public Habitat(Dimension areaSize,int n1,double p1,int n2,int kPercent,long adultLifetime,long chickLifetime){
        Habitat.areaSize=areaSize;
        this.n1=n1;this.p1=p1;this.n2=n2;this.kPercent=kPercent;
        this.adultLifetime=adultLifetime;this.chickLifetime=chickLifetime;
        this.simulationTime=0;this.lastAdultTime=0;this.lastChickTime=0;
        this.birds=new LinkedList<>();this.idSet=new TreeSet<>();this.birthTimeMap=new HashMap<>();
    }

    public LinkedList<Bird> getBirds() {
        return birds;
    }

    public void setGlobalAdultActive(boolean a){globalAdultActive=a;}
    public void setGlobalChickActive(boolean a){globalChickActive=a;}

    public void addAllBirds(List<Bird> newBirds) {
        birds.addAll(newBirds);
    }

    public long getSimulationTime(){return simulationTime;}
    public HashMap<Integer,Long> getBirthTimeMap(){return birthTimeMap;}

    private int generateUniqueId(){
        int id; do{id=1000+(int)(Math.random()*9000);}while(idSet.contains(id));
        return id;
    }

    public void updateGlobalAngles(){
        double a=Math.random()*2*Math.PI;
        globalAdultAngle=a;globalChickAngle=a;
    }

    public void update(long elapsedTime){
        simulationTime=elapsedTime;
        // взрослые
        if(simulationTime-lastAdultTime>=n1){
            if(Math.random()<p1){
                int id=generateUniqueId();
                birds.add(new AdultBird(randomX(),randomY(),simulationTime,adultLifetime,id));
                idSet.add(id); birthTimeMap.put(id,simulationTime);
            }
            lastAdultTime=simulationTime;
        }
        // птенцы
        if(simulationTime-lastChickTime>=n2){
            int adults=(int)birds.stream().filter(b->b instanceof AdultBird).count();
            int chicks=(int)birds.stream().filter(b->b instanceof Chick).count();
            if(adults>0 && chicks*100<adults*kPercent){
                int id=generateUniqueId();
                birds.add(new Chick(randomX(),randomY(),simulationTime,chickLifetime,id));
                idSet.add(id); birthTimeMap.put(id,simulationTime);
            }
            lastChickTime=simulationTime;
        }
        // удалить старых
        Iterator<Bird> it=birds.iterator();
        while(it.hasNext()){
            Bird b=it.next();
            if(simulationTime-b.birthTime>=b.lifetime){
                it.remove();idSet.remove(b.id);birthTimeMap.remove(b.id);
            }
        }
        // движение
        for(Bird b:birds){
            if(b instanceof AdultBird && globalAdultActive){
                b.x+= (int)(5*Math.cos(globalAdultAngle));
                b.y+= (int)(5*Math.sin(globalAdultAngle));
            } else if(b instanceof Chick && globalChickActive){
                b.x+= (int)(3*Math.cos(globalChickAngle));
                b.y+= (int)(3*Math.sin(globalChickAngle));
            } else {
                b.update(elapsedTime);
            }
        }
    }

    public void draw(Graphics g,boolean showTime){
        for(Bird b:birds) b.draw(g);
        if(showTime){
            g.drawString("Время: "+simulationTime/1000.0+" с",10,20);
        }
    }

    public void clear(){birds.clear();idSet.clear();birthTimeMap.clear();}

    public String getStatistics(){
        int a=(int)birds.stream().filter(b->b instanceof AdultBird).count();
        int c=(int)birds.stream().filter(b->b instanceof Chick).count();
        return "Взрослых:"+a+", птенцов:"+c+", время:"+simulationTime/1000.0+"с";
    }

    /** Вариант 12: уменьшить птенцов на percent% */
    public void reduceChicksBy(int percent) {
        // Собираем всех птенцов в отдельный список
        List<Bird> chicks = new ArrayList<>();
        for (Bird b : birds) {
            if (b instanceof Chick) {
                chicks.add(b);
            }
        }

        int total = chicks.size();
        int toRemove = total * percent / 100;

        // Удаляем первые toRemove птиц из оригинального списка и вспомогательных структур
        for (int i = 0; i < toRemove && i < chicks.size(); i++) {
            Bird chick = chicks.get(i);
            birds.remove(chick);
            idSet.remove(chick.id);
            birthTimeMap.remove(chick.id);
        }
    }

    private int randomX(){return (int)(Math.random()*areaSize.width);}
    private int randomY(){return (int)(Math.random()*areaSize.height);}
}
