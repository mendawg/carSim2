public class Camera {


    private static int id = 0;
    private int cameraId;

    private Way occupiedWay;

    public Camera(Way way) {
        this.cameraId = id++;
        this.occupiedWay = way;
    }




}
