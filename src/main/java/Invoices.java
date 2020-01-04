import com.baidu.aip.ocr.AipOcr;
import com.csvreader.CsvWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.json.JSONObject;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

/**
 * Created by gn.liu on 2018/10/17
 */
public class Invoices {

    //默认至
    public static  String appId = "14462147";
    public static  String apiKey = "xBnTuMrnXFtDWeplkfXeobz4";
    public static  String secretKey = "WE9O670tUZ4VZBNUzHrEDW9NviAAWIk7";
    public static  String root = "D:\\Vat";


    public static void main(String[] args) {
        System.out.println("Strating...");
        init();
        runPDFVat2CSV();
        System.out.println("Compeleted...");
    }

    private static void init() {
        Properties p = new Properties();
        try {
            InputStream in =new BufferedInputStream(new FileInputStream("C:\\app.properties"));
            p.load(in);
        } catch (IOException e) {
            System.out.println("无法加载配置文件！");
            e.printStackTrace();
        }
        if (!p.get("APP_ID").equals("")&&!p.get("API_KEY").equals("")&&!p.get("SECRET_KEY").equals("")){
            appId = (String) p.get("APP_ID");
            apiKey = (String) p.get("API_KEY");
            secretKey = (String) p.get("SECRET_KEY");
        }
        root = (String) p.get("ROOT");
    }

    private static void runPDFVat2CSV() {
        File rootFolder = new File(root);
        if (rootFolder.exists()){
            traverseFolder2(root);
        }else {
          System.out.println("请将PDF文件放入 "+root+"文件夹");
        }
    }

    /*
    递归方式遍历处理
     */
    public static void traverseFolder2(String path) {

        File file = new File(path);
        if (file.exists()) {
            File[] files = file.listFiles();
            if (files.length == 0) {
                System.out.println("文件夹是空的!");
                return;
            } else {
                for (File file2 : files) {
                    if (file2.isDirectory()) {
                        //System.out.println("文件夹:" + file2.getAbsolutePath());
                        traverseFolder2(file2.getAbsolutePath());
                    } else {
                         //System.out.println("文件:" + file2.getAbsolutePath());
                        //todo
                        if (file2.getName().contains(".pdf"))
                            handlerPDF(file2);
                    }
                }
            }
        } else {
            System.out.println("文件不存在!");
        }
    }

    private static void handlerPDF(File pdfFile) {
        //pdf分割为图片保存到与pdf同名的folder中
        //AI识别,同时在每个PDF对应的folder下产生一个CVS文件方便核对;
        String csvFile = pdfFile.getAbsolutePath().replaceAll(".pdf",".csv");
        CsvWriter csvWriter = new CsvWriter(csvFile,',', Charset.forName("GBK"));
        try {
            csvWriter.writeRecord(new String[]{"发票号","开票日期","购买方","价税合计","销售方"});
        } catch (IOException e) {
            e.printStackTrace();
        }
        List<String> imgPath = pdfToImagePath(pdfFile.getAbsolutePath());
        for (String img:imgPath){
            if (img.contains(".jpg")){
                try {
                    csvWriter.writeRecord(callBaiduApi(img));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        csvWriter.close();
    }

    /*
    调用baiduAPI
     */
    public static String[] callBaiduApi(String img) {
        // 初始化一个AipOcr
        AipOcr client = new AipOcr(appId, apiKey, secretKey);

        // 可选：设置网络连接参数
        client.setConnectionTimeoutInMillis(20000);
        client.setSocketTimeoutInMillis(600000);

        // 可选：设置代理服务器地址, http和socket二选一，或者均不设置
//         client.setHttpProxy("proxy_host", proxy_port);  // 设置http代理
//         client.setSocketProxy("proxy_host", proxy_port);  // 设置socket代理

        // 可选：设置log4j日志输出格式，若不设置，则使用默认配置
        // 也可以直接通过jvm启动参数设置此环境变量
        // System.setProperty("aip.log4j.conf", "path/to/your/log4j.properties");

        HashMap op = new HashMap<String, String>();
        //发票识别,高精度
        op.put("accuracy", "high");
        JSONObject res = client.vatInvoice(img, op);
        //System.out.println(res);
        if (!res.has("error_code")) {
//            System.out.println("发票号:  " + res.getJSONObject("words_result").getString("InvoiceNum"));
//            System.out.println("开票日期:  " + res.getJSONObject("words_result").getString("InvoiceDate"));
//            System.out.println("购买方:  " + res.getJSONObject("words_result").getString("PurchaserName"));
//            System.out.println("价税合计:  " + res.getJSONObject("words_result").getString("AmountInFiguers"));
            return new String[]{
                    res.getJSONObject("words_result").getString("InvoiceNum"),
                    res.getJSONObject("words_result").getString("InvoiceDate"),
                    res.getJSONObject("words_result").getString("PurchaserName"),
                    res.getJSONObject("words_result").getString("AmountInFiguers"),
                    res.getJSONObject("words_result").getString("SellerName")
            };
        }
        return null;
    }


    public static List<String> pdfToImagePath(String filePath) {
        List<String> list = new ArrayList<String>();
        String fileDirectory = filePath.substring(0, filePath.lastIndexOf("."));//获取去除后缀的文件路径
        //图片放在D:\\VatImg
        fileDirectory =fileDirectory.substring(0,root.length())+"Image"+fileDirectory.substring(root.length());

        String imagePath;
        File file = new File(filePath);
        try {
            File f = new File(fileDirectory);
            if (!f.exists()) {
                f.mkdirs();
            }
            PDDocument doc = PDDocument.load(file);
            PDFRenderer renderer = new PDFRenderer(doc);
            int pageCount = doc.getNumberOfPages();
            for (int i = 0; i < pageCount; i++) {
                // 方式1,第二个参数是设置缩放比(即像素)
                // BufferedImage image = renderer.renderImageWithDPI(i, 296);
                // 方式2,第二个参数是设置缩放比(即像素)
                BufferedImage image = renderer.renderImage(i, 4f);  //第二个参数越大生成图片分辨率越高，转换时间也就越长
                imagePath = fileDirectory + "\\" + i + ".jpg";
                ImageIO.write(image, "PNG", new File(imagePath));
                list.add(imagePath);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return list;
    }

    /*
     图片分割
     */
    @Test
    public void splitImage() throws IOException {

        //String originalImg = "C:\\img\\split\\a380_1280x1024.jpg";
        String originalImg = "D:\\vat\\invoices\\1.jpg";
        // 读入大图
        File file = new File(originalImg);
        FileInputStream fis = new FileInputStream(file);
        BufferedImage image = ImageIO.read(fis);

        // 分割成4*4(16)个小图
        int rows = 2;
        int cols = 1;
        int chunks = rows * cols;

        // 计算每个小图的宽度和高度
        int chunkWidth = image.getWidth() / cols;
        int chunkHeight = image.getHeight() / rows;

        int count = 0;
        BufferedImage imgs[] = new BufferedImage[chunks];
        for (int x = 0; x < rows; x++) {
            for (int y = 0; y < cols; y++) {
                //设置小图的大小和类型
                imgs[count] = new BufferedImage(chunkWidth, chunkHeight, image.getType());

                //写入图像内容
                Graphics2D gr = imgs[count++].createGraphics();
                gr.drawImage(image, 0, 0,
                        chunkWidth, chunkHeight,
                        chunkWidth * y, chunkHeight * x,
                        chunkWidth * y + chunkWidth,
                        chunkHeight * x + chunkHeight, null);
                gr.dispose();
            }
        }

        // 输出小图
        for (int i = 0; i < imgs.length; i++) {
            //ImageIO.write(imgs[i], "jpg", new File("C:\\img\\split\\img" + i + ".jpg"));
            ImageIO.write(imgs[i], "jpg", new File("d:\\imgvat\\img" + i + ".jpg"));
        }

        System.out.println("完成分割！");
    }


}
