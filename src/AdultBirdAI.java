public class AdultBirdAI extends BaseAI {
    private Habitat habitat;
    public AdultBirdAI(Habitat habitat) {
        this.habitat = habitat;
    }
    @Override
    protected void updateAI() {
        habitat.updateGlobalAngles();
    }
    @Override
    protected long getDirectionChangePeriod() {
        return 3000;
    }
    // остановка потока
    public void stopAI() {
        running = false;
    }
}
