#include <jni.h>
#include <android/log.h>
#include "fann.h"
// #include "floatfann.h"
// #include <com_golab_roundedgev2_MainActivity.h>

#define  LOG_TAG "FANN TEST"
#define  LOG(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)

// using namespace std;

extern "C" {
JNIEXPORT jfloatArray JNICALL Java_golab_roundedgendk_MainActivity_testFann
(JNIEnv *env, jclass thisz) {
	fann_type *calc_out;
	unsigned int i;
	int ret = 0;
	struct fann *ann;
	struct fann_train_data *data;

	//テストデータ用の配列
	fann_type input[21];

	LOG("Creating network.\n");

	//ネットワークの読み込み(ファイルから)
	//    ann = fann_create_from_file("round_float.net");
	ann = fann_create_from_file("/data/data/golab.roundedgendk/files/round_float.net");

	//エラー処理
	if(!ann)
	{
		LOG("Error creating ann --- ABORTING.\n");
		return NULL;
	}
	//接続状態の表示
	fann_print_connections(ann);
	//パラメータの表示
	fann_print_parameters(ann);

	FILE *fp;

	// テストデータの読み込み
	fp = fopen("/data/data/golab.roundedgendk/files/round_test.data", "r");     /*  読み込みモードでファイルをオープン  */
	if(fp == NULL) {
		LOG("ファイルを開くことが出来ませんでした．¥n");
		return NULL;
	}

	for (int i = 0; i < 21; i++) {
		fscanf(fp, "%f", &(input[i]) );     /*  1行読む  */
	}

	fclose(fp);

	for (int i = 0; i < 21; i++) {
		LOG("input[%d] = %f\n", i, input[i]);   /* 配列内容の表示 */
	}

	LOG("\nTesting network.\n");

	calc_out = fann_run(ann, input);

	//    for (int i = 0; i < 40; i++) {
	//        LOG("out[%d] = %f\n", i, calc_out[i]);   /* 配列内容の表示 */
	//    }


	//ネイティブ型のfloat*からjfloatArrayへ変換
	jfloatArray result;
	result = env->NewFloatArray(40);
	env->SetFloatArrayRegion(result, 0, 40, calc_out);

	//後片付け
	LOG("Cleaning up.\n");
	fann_destroy_train(data);
	fann_destroy(ann);

	//ニューラルネットワークの出力を返す
	return result;
}
}
