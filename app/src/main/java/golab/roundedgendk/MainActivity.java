package golab.roundedgendk;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.TextView;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;

public class MainActivity extends Activity {

    private int sets = 0;
    private boolean isFisnish = false;
    private TextView textview;		// テキストビュー
    private TextView stimuview;
    final static int CircleX = 200; // 円のX座標
    final static int CircleY = 200; // 円のY座標
    final static int numOfArea = 12; // 領域の数
    final int num_input = 21; // ニューラルネットワークへの入力数
    private Button btn;
    private int area = -1; // どの領域か
    private int priorAreaListSize = 0;  //前回のデータ登録時のareaListの大きさ
    private boolean directionFlag;  //進行方向が正の向きならtrue, 負の向きならfalse
    private int enterChar = 0; // 入力中の文字をインデックスで表す（0 ~ 25）
    private int num_char = 0; // 入力済みの文字の数
    private int num_backspace = 0;
    private boolean isFinish = false; // 入力が終了したかどうか
    private static int TRIALS = 20;
    ArrayList<Integer> areaList = new ArrayList<Integer>();
    ArrayList<Double> angleList = new ArrayList<Double>();
    ArrayList<Integer[]> dataList = new ArrayList<Integer[]>();
    ArrayList<Integer[]> outputList = new ArrayList<Integer[]>(); // ファイル出力用にデータを保存する
    private String enterText = "";
    ArrayList<String> enterList = new ArrayList<String>();	// 入力された文字列を保持する
    ArrayList<String> stimuList = new ArrayList<String>(); // 課題文のリスト
    ArrayList<String[]> scoreList = new ArrayList<String[]>();	// 評価に必要なデータを保持する
    ArrayList<String[]> resultList = new ArrayList<String[]>();
    Random random = new Random();
    ToneGenerator toneGenerator = new ToneGenerator(AudioManager.STREAM_SYSTEM, ToneGenerator.MAX_VOLUME);


    // タイマー用変数
    private float mLaptime = 0.0f;
    MyTimerTask timerTask = null;
    Timer   mTimer   = null;
    Handler mHandler = new Handler();

    private int stimuNo;

    static {
        try {
            System.loadLibrary("gnustl_shared");
            System.loadLibrary("RoundEdgeV2");
        } catch (UnsatisfiedLinkError e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {

                // テキストビューの取得
                textview = (TextView) findViewById(R.id.enterText);
                textview.setText(" ");

                stimuview = (TextView) findViewById(R.id.stimulus);

                loadStimuli();

                stimuNo = (int)(Math.random()*stimuList.size());

                stimuview.setText(stimuList.get(stimuNo));
//                stimuview.setText("");


                //buttonを取得
                btn = (Button)findViewById(R.id.button);

                btn.setOnClickListener(new View.OnClickListener(){

                    //インターフェイスを実装 implements OnClickListener
                    public void onClick(View v) {
                        if(isFinish)
                            return;

                        if(mTimer != null){ // タイマー終了処理
                            mTimer.cancel();
                            mTimer = null;
                        }

                        scoreFileOutput();

                        mLaptime = 0.0f;
                        num_backspace = 0;

                        sets++;

                        num_char = 0;
                        setResultList();
                        outputList.clear();
                        enterList.clear();
                        scoreList.clear();
                        enterText = "";
                        textview.setText(enterText);

                        if(sets >= TRIALS) {  // 20
                            isFinish = true;
                            btn.setVisibility(View.INVISIBLE);
                            stimuview.setText("Finish");
                            resultFileOutput();
                            return;
                        }

                        stimuNo = (int)(Math.random()*stimuList.size());
//                        stimuview.setText(stimuList.get(stimuNo));
                        stimuview.setText("");

                    }

                });

            }
        });

        // ニューラルネットワークの構成を/res/raw/round_floatから内部ファイル（ローカルファイル）にコピー
        File dir = new File(this.getFilesDir().getPath());
        loadFile(dir, R.raw.round_float_00015, "round_float.net");
    }


    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {

        if(isFinish)
            return false;

        // タッチ座標の角度を極座標変換で求める
        double theta = getTheta(event.getX() - CircleX, CircleY - event.getY());
        // タッチしているのがどのエリアかを極座標から求める
        area = whichArea(theta);

        switch(event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                angleList.add(theta);
                areaList.add(area);

                if(mTimer == null){ // タイマー開始処理
                    //タイマーの初期化処理
                    timerTask = new MyTimerTask();
                    mTimer = new Timer(true);
                    mTimer.schedule( timerTask, 50, 50);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if(areaList.size() > 0)
                    if(areaList.get( areaList.size() - 1 ) != area) {
                        boolean new_direction = whichDirection(area);
                        if(areaList.size() == 1) {
                            directionFlag = new_direction;
                            setFirstDataList();
                        }
                        if(new_direction != directionFlag) {
                            directionFlag = new_direction;
                            setDataList();
                        }
                        areaList.add(area);
                    }
                break;

            case MotionEvent.ACTION_UP:
                if(areaList.size() == 1)
                    setFirstDataList();
                if(areaList.get( areaList.size() - 1 ) != area)
                    areaList.add(area);

                setDataList();
                setOutputList();

                angleList.clear();
                areaList.clear();
                dataList.clear();
                //enterChar++; // 入力文字を次の文字へ（現在はaからzへ順番）
                num_char++;
                priorAreaListSize = 0;

                /********テストファイル作成用の関数の呼び出し*********/
                /********直近に入力されたパターンをテストパターンとして保存します*******/
                testFileOutput();
                /************************************************/

                // Call the native fann test
                float[] out = testFann();

                // textview.setText(whatChar(arrayMaxIndex(out)));

                String str = whatChar(arrayMaxIndex(out));	// 識別結果の配列から推定文字をstrに格納

                // 入力文字列のリストへの格納処理
                if(!str.equals("Miss")) {
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP);
                    if(str.equals("Backspace") && enterList.size() != 0) {
                        enterList.remove(enterList.size() - 1);
                        num_backspace++;
                    }
                    else if(!str.equals("Backspace"))
                        enterList.add(str);
                    StringBuilder sb = new StringBuilder();
                    for(int i = 0; i < enterList.size(); i++) {
                        sb.append(enterList.get(i));
                    }
                    enterText = new String(sb);
                }
                // テキストビューへ出力
                textview.setText(enterText);

                setScore();

                for(int i = 0; i < 40; i++)
                    Log.d("Result", "out[" + i + "] = " + out[i] );

                break;
        }
        return super.dispatchTouchEvent(event);
    }

    /**
     * X, Y座標から極座標変換を行って角度を求める関数
     */
    private double getTheta(Float x, Float y){
        return (Math.atan2(y, x) * (180.0 / Math.PI) + 360.0) % 360.0;
    }

    /**
     * 角度からどの領域かを判定する関数
     */
    private int whichArea(double th) {
        int area = -1;
        for(int i = 0; i < numOfArea; i++) {
            if(th >= 30 * i && th < 30 * (i + 1))
                area = i;
        }
        return area;
    }

    /**
     * タッチエリアの移動状況から進行方向を判定する
     */
    private boolean whichDirection(int area) {
        if (area == 0 && areaList.get(areaList.size() - 1) == 11)
            return true;
        if (area == 11 && areaList.get(areaList.size() - 1) == 0)
            return false;
        if (area > areaList.get(areaList.size() - 1))
            return true;
        else
            return false;
    }

    /**
     * boolean型の真偽をInteger型の1or0で返す
     */
    private Integer convertBoolean(boolean bl) {
        if(bl)
            return 1;
        else
            return 0;
    }

    /**
     * 初回の入力データを登録する
     */
    private void setFirstDataList() {
        Integer data[] = new Integer[2];
        data[0] = areaList.get( areaList.size() - 1 );
        data[1] = convertBoolean(directionFlag);
        dataList.add(data);

        priorAreaListSize = areaList.size();
    }

    /**
     * ２回目以降の入力データの登録
     * 初回との違いは周回数をカウントする点（本システムでは使用しないが一応実装）
     */
    private void setDataList() {
        if(priorAreaListSize < 1)
            return;

        int laps = 0;
        if(areaList.size() >= priorAreaListSize + 12)
            laps = 1;

        Integer data[] = new Integer[2];
        data[0] = areaList.get( areaList.size() - 1 );
        data[1] = laps;
        dataList.add(data);

        priorAreaListSize = areaList.size();
    }

    // 一試行分の入力データを登録する関数
    // 入力データの方式はプログラム先頭を参照
    private void setOutputList() {

        if(dataList == null)
            return;

        // final int num_input = 37; // ニューラルネットワークへの入力数
        Integer data[] = new Integer[num_input];

        for(int i = 0; i < num_input; i++) // 配列の各要素を-1で初期化
            data[i] = -1;

        for(int i = 0; i < dataList.size(); i++) {
            if(i == 0) {
                int n = dataList.get(i)[0];         // 指を下ろした領域を２進数で表記する
                if( n >= 8 ) {
                    data[0] = 1;
                    n -= 8;
                }
                if( n >= 4 ) {
                    data[1] = 1;
                    n -= 4;
                }
                if( n >= 2 ) {
                    data[2] = 1;
                    n -= 2;
                }
                if( n >= 1) {
                    data[3] = 1;
                }
                if(dataList.get(i)[1] == 1)         // 進行方向を1 or -1で登録
                    data[4] = 1;
            }

            else if(i < dataList.size() - 1) {
                data[ dataList.get(i)[0] + 5 ] = 1; // 折り返しのあった領域を1に
            }

            else {
                int n = dataList.get(i)[0]; // 指を離した領域を２進数で表記
                if( n >= 8 ) {
                    data[17] = 1;
                    n -= 8;
                }
                if( n >= 4 ) {
                    data[18] = 1;
                    n -= 4;
                }
                if( n >= 2 ) {
                    data[19] = 1;
                    n -= 2;
                }
                if( n >= 1) {
                    data[20] = 1;
                }
            }
        }
        Log.d("setOutputList", Arrays.toString(data));
        outputList.add(data); // 出力データ保持用のリストに追加
    }

    private void setScore() {
        Log.d("Length","stimu :" + stimuList.get(stimuNo).length() + ", enter :" + enterText.length());

        if(enterText.length() < 1)
            return;

        String score[] = new String[8];
        int num_diff = 0;

        for(int i = 0; i < stimuList.get(stimuNo).length(); i++) {
            if(i > enterText.length() - 1) {
                num_diff += stimuList.get(stimuNo).length() - enterText.length();
                break;
            }
            if(!stimuList.get(stimuNo).substring(i, i+1).equals(enterText.substring(i, i+1)))
                num_diff++;
        }

        if(enterText.length() > stimuList.get(stimuNo).length())
            num_diff += enterText.length() - stimuList.get(stimuNo).length();

        score[0] = Integer.toString(stimuNo);
        score[1] = stimuList.get(stimuNo);
        score[2] = Integer.toString(stimuList.get(stimuNo).length());
        score[3] = enterText;
        score[4] = Integer.toString(enterText.length());
        score[5] = Integer.toString(num_backspace);
        score[6] = Integer.toString(num_diff);
        score[7] = Float.toString(mLaptime);

        scoreList.add(score);

    }

    private void setResultList() {
        if(scoreList.size() < 1)
            return;

        String str[] = new String[4];
        int index = scoreList.size() - 1;

        str[0] = Integer.toString(sets);
        str[1] = Double.toString(Double.parseDouble(scoreList.get(index)[4])
                / Double.parseDouble(scoreList.get(index)[7]) * 60000);
        str[2] = Double.toString(Double.parseDouble(scoreList.get(index)[5])
                / ( Double.parseDouble(scoreList.get(index)[4])
                + Double.parseDouble(scoreList.get(index)[5])));
        str[3] = Double.toString(Double.parseDouble(scoreList.get(index)[6])
                / ( Double.parseDouble(scoreList.get(index)[4])
                + Double.parseDouble(scoreList.get(index)[5])));

        resultList.add(str);
    }

    /**
     * ニューラルネットワークに渡す識別用の入力
     */
    private void testFileOutput() {
        if(num_char <= 0)
            return;

        OutputStream out;
        try {
            out = openFileOutput("round_test.data", MODE_PRIVATE);
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(out,"UTF-8"));

            if(outputList == null)
                return;

            for(int j = 0; j < num_input; j++) {
                writer.append(String.valueOf(outputList.get(num_char - 1)[j]));
                writer.println();
            }

            writer.close();
        } catch (IOException e) {
            // TODO 自動生成された catch ブロック
            e.printStackTrace();
        }
    }


    private void scoreFileOutput() {
        if(scoreList.size() <= 0)
            return;

        String Folder = getApplicationInfo().dataDir + "/files/result/";

        File Dir = new File( Folder );
        if( !Dir.exists() )
            Dir.mkdirs();

        FileOutputStream out;

        try {
//          out = context.openFileOutput(enterChar + ".csv",context.MODE_PRIVATE);
//          PrintWriter writer = new PrintWriter(new OutputStreamWriter(out,"UTF-8"));
            out = new FileOutputStream(Folder + sets + ".csv", false);
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(out,"UTF-8"));


            //追記する
            writer.append("Stimulus_No");
            writer.append(",");
            writer.append("Stimulus");
            writer.append(",");
            writer.append("Stimulus_Size");
            writer.append(",");
            writer.append("EnterText");
            writer.append(",");
            writer.append("EnterText_Size");
            writer.append(",");
            writer.append("Backspace");
            writer.append(",");
            writer.append("Diff");
            writer.append(",");
            writer.append("Time");
            writer.println();

            for(int i = 0; i < scoreList.size(); i++) {
                for(int j = 0; j < scoreList.get(0).length; j++) {
                    writer.append(scoreList.get(i)[j]);
                    if(j < 7) writer.append(",");
                }
                writer.println();
            }

            writer.close();
        } catch (IOException e) {
            // TODO 自動生成された catch ブロック
            e.printStackTrace();
        }

    }

    private void resultFileOutput() {
        if(resultList.size() <= 0)
            return;

        String Folder = getApplicationInfo().dataDir + "/files/result/";

        File Dir = new File( Folder );
        if( !Dir.exists() )
            Dir.mkdirs();

        FileOutputStream out;

        try {
//          out = context.openFileOutput(enterChar + ".csv",context.MODE_PRIVATE);
//          PrintWriter writer = new PrintWriter(new OutputStreamWriter(out,"UTF-8"));
            out = new FileOutputStream(Folder + "result.csv", false);
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(out,"UTF-8"));


            //追記する
            writer.append("Set");
            writer.append(",");
            writer.append("CPM");
            writer.append(",");
            writer.append("Fixed_err");
            writer.append(",");
            writer.append("Non_Fixed_err");
            writer.println();

            for(int i = 0; i < resultList.size(); i++) {
                for(int j = 0; j < resultList.get(0).length; j++) {
                    writer.append(resultList.get(i)[j]);
                    if(j < 3) writer.append(",");
                }
                writer.println();
            }

            writer.close();
        } catch (IOException e) {
            // TODO 自動生成された catch ブロック
            e.printStackTrace();
        }

    }

    /**
     * 配列の中の最大値のインデックスを返します。
     * @param array 要素の最大値のインデックスを求める配列
     * @return 配列の中の最大値のインデックス
     */
    public static int arrayMaxIndex(float[] array){
        int index = 0;
        for(int i=1; i<array.length; i++)
            index = (array[index] >= array[i]) ? index : i;

        if(array[index] == -1.0f)
            return -1;

        return index;
    }

    /**
     * 添字からどの入力文字かを判定する関数
     */
    private String whatChar(int index) {
        String target = " ";
        String str = "abcdefghijklmnopqrstuvwxyz0123456789";

        if(index < 0)
            return "Miss";

        if(index < 36)
            target = str.substring(index, index+1);
        else
            switch(index) {
                case 36:
                    target = "_";
                    break;
                case 37:
                    target = "Backspace";
                    break;
                case 38:
                    target = ",";
                    break;
                case 39:
                    target = ".";
                    break;
            }
        return target;
    }

    /**
     * res/rawディレクトリに保存されたデータファイルを読み込んで，native側（c, c++）で読み込めるように
     * File型変数dirで指定したディレクトリ（基本は/data/data/[package]/files）にコピーする
     */
    private File loadFile(File dir, int res, String filename) {
        try {
            InputStream is = this.getResources().openRawResource(res);
            File target = new File(dir, filename);
            FileOutputStream os = new FileOutputStream(target);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();
            return target;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * res/rawから課題文を読み込んでリストstimuListへ格納する
     */
    private void loadStimuli() {

        InputStream is = this.getResources().openRawResource(R.raw.stimuli);
        BufferedReader br = null;
        // StringBuilder sb = new StringBuilder();
        try{
            try {
                br = new BufferedReader(new InputStreamReader(is));
                String str;
                while((str = br.readLine()) != null){
                    stimuList.add(str);
                }
            } finally {
                if (br !=null) br.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class MyTimerTask extends TimerTask{

        @Override
        public void run() {
            // mHandlerを通じてUI Threadへ処理をキューイング
            mHandler.post( new Runnable() {
                public void run() {

                    //実行間隔分を加算処理
                    mLaptime +=  50d;

                    //計算にゆらぎがあるので小数点第1位で丸める
                    //BigDecimal bi = new BigDecimal(mLaptime);
                    //float outputValue = bi.setScale(1, BigDecimal.ROUND_HALF_UP).floatValue();

                    //現在のLapTime
                    //mTextView.setText(Float.toString(outputValue));
                }
            });
        }
    }

    private static native float[] testFann();

}
