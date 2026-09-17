<template>
  <div class="app-container">
    <el-form
      ref="serviceAreaDetailForm"
      size="medium"
      label-width="120px"
      :model="infos"
      :inline="false"
      :rules="rules"
      :disabled="source==='01'"
    >
      <el-form-item label="配置名称" >
        <el-select v-model="infos.configId" placeholder="请选择配置名称" class="el-input" >
          <el-option v-for="item in types" :key="item.configId" :label="item.configName" :value="item.configId" />
        </el-select>
      </el-form-item>

      <el-form-item label="是否标记为标签" prop="labelFlag">
        <el-select v-model="infos.labelFlag" placeholder="是否标记为标签">
          <el-option label="是" value="01"/>
          <el-option label="否" value="02"/>
        </el-select>
      </el-form-item>

      <el-form-item label="是否为特色服务" prop="featured">
        <el-select v-model="infos.featured" placeholder="是否为特色服务">
          <el-option label="是" value="01"/>
          <el-option label="否" value="02"/>
        </el-select>
      </el-form-item>


      <el-form-item label="配置图片" prop="serviceInfoPicUrl">
        <el-upload
          action="#"
          list-type="picture-card"
          :auto-upload="true"
          :http-request="uploadFile"
          :limit="1"
          :on-exceed="handleExceed"
          :before-upload="beforeAvatarUpload"
          :before-remove="beforeRemove"
          :file-list="fileList"
        >
          <i slot="default" class="el-icon-plus"></i>
          <div slot="file" slot-scope="{file}">
            <img class="el-upload-list__item-thumbnail" :src="file.url" alt="">
            <span class="el-upload-list__item-actions">
                <span class="el-upload-list__item-preview" @click="handlePictureCardPreview(file)">
                  <i class="el-icon-zoom-in"></i>
                </span>
                <span v-if="source!=='01'" class="el-upload-list__item-delete" @click="handleRemove(file)">
                  <i class="el-icon-delete"></i>
                </span>
              </span>
          </div>
        </el-upload>
        <el-dialog :visible.sync="dialogVisible">
          <img width="100%" :src="dialogImageUrl" alt="">
        </el-dialog>
      </el-form-item>

      <el-form-item v-if="source!=='01'">
        <el-button type="primary" @click="onSubmit">确定</el-button>
      </el-form-item>
    </el-form>

<!--    &lt;!&ndash;    高德地图&ndash;&gt;-->
<!--    <el-dialog :visible.sync="dialogFormVisible" append-to-body>-->
<!--      <amap @on-marker-address-load="backFillAddress($event)" ref="amapRef"/>-->
<!--      <div slot="footer" class="dialog-footer" style="text-align: center;">-->
<!--        <el-button type="primary" @click="chooseMapValue"> 确认</el-button>-->
<!--        <el-button type="primary" @click="cancelMapShow"> 取消</el-button>-->
<!--      </div>-->
<!--    </el-dialog>-->
  </div>
</template>

<script>
import {getServiceAreaById, insertServiceAreaDetail, updateServiceArea} from '@/api/serviceAreaDetail'
import Amap from "@/views/map";
import { uploadMerchantImage } from "@/api/merchant";
import { getSysConfigByType } from "@/api/news";
import {getInfo, updateVehicle} from "@/api/userVehicle";


export default {
  name: 'Config',
  components: {Amap},
  props: {
    id: {
      type: String,
      default: null
    },
    source: {
      type: String,
      default: null
    },
    // serviceInfoName: {
    //   serviceInfoName: String,
    //   default: null
    // }

  },
  data() {
    return {
      infos: {
        id: null,
        configId: null,
        serviceInfoPicUrl: null,
        serviceAreaId:null

      },
      types: [],
      dialogFormVisible: false,
      // 图片开始

      dialogImageUrl: '',
      dialogVisible: false,
      disabled: false,
      fileList: [],
      // 图片结束
      rules: {
        configId: [
          {required: true, message: '请输入配置名称', trigger: 'blur'}
        ],
        // serviceInfoPicUrl: [
        //   {required: true, message: '请输入配置图标', trigger: 'blur'}
        // ],

      }
    }
  },

  created() {
    // if (this.id !== null) {
    //   this.getInfo(this.id)
    // }

    this.getTypes()
    //this.infos.serviceInfoNames = this.serviceInfoNames
  },
  methods: {
    getTypes() {
      getSysConfigByType('serviceInfoConfig').then(res => {
        if (res.code === '0') {
          this.types = res.clazz
        }
      })
    },
    // getInfo(id) {
    //   getServiceAreaById({id: id}).then(res => {
    //     if (res.code === '0') {
    //       this.infos = res.clazz
    //       if (this.infos.serviceAreaPicture !== undefined && this.infos.serviceAreaPicture !== null && this.infos.serviceAreaPicture !== '') {
    //         this.fileList.push({
    //           url: res.clazz.readUrl
    //         });
    //       } else {
    //         this.fileList = [];
    //       }
    //
    //     }
    //   })
    // },

    // 高德地图
    // showMap() {
    //   this.dialogFormVisible = true;
    // },
    // backFillAddress({address, lat, lng, name, addressComponent}) {
    //   this.infos.address = address;
    //   this.infos.longitude = String(lng);
    //   this.infos.latitude = String(lat);
    // },
    // chooseMapValue() {
    //   this.dialogFormVisible = false;
    // },
    // cancelMapShow() {
    //   this.dialogFormVisible = false;
    // },

    onSuccess(res, file) {
      this.banner.logo = URL.createObjectURL(file.raw)
    },
    /**上传图片开始**/
    //超出数量提醒
    handleExceed(files, fileList) {
      this.$message.warning(`上传文件个数已超限！`);
    },
    beforeRemove(file, fileList) {
      return true;
    },
    // 限制用户上传的图片格式和大小
    beforeAvatarUpload(file) {
      if (file.type == 'image/jpeg' || file.type == 'image/png' || file.type == 'image/jpg') {

      } else {
        this.$message.error('请上传 jpg,jpeg,png 格式图片');
        return false;
      }

      const isLtM = file.size / 1024 / 1024 < 20
      if (!isLtM) {
        this.$message.error('上传图片大小不能超过 20MB!')
        return false;
      }

      return true;
    },
    // 查看大图方法，默认无需修改
    handlePictureCardPreview(file) {
      this.dialogImageUrl = file.url;
      this.dialogVisible = true;
    },

    //删除
    handleRemove(file) {
      this.fileList = [];
      this.infos.serviceInfoPicUrl = "";
    },

    //上传
    uploadFile(headers) {
      var form = new FormData();
      form.append("file", headers.file);
      form.append("imageType", 0);
      form.append("moduleName", "配置");
      uploadMerchantImage(form).then((data) => {
        if (data.success) {
          this.fileList.push({
            url: data.clazz.readUrl
          });
          this.infos.serviceInfoPicUrl = data.clazz.imageUrl
          this.$message.success('上传成功!');
        } else {
          this.$message.error('上传失败!');
        }
      }).catch((err) => {
        this.$message.error('上传异常!' + err);
      })
    },
    /**上传图片结束**/

    //提交按钮
    onSubmit: function() {

      this.$refs["serviceAreaDetailForm"].validate(valid => {
        if (valid) {
          if (this.id !== null) {
            this.infos['serviceAreaId'] = this.id
            insertServiceAreaDetail(this.infos).then(res => {
              if (res.code === '0') {
                this.$message.success('操作成功')
                this.$emit('fatherMethod')
              } else {
                // this.$message.error('操作失败')
              }
            })
          }
        }
      });
    },


  }
}
</script>

<style scoped lang="scss">
.app-container {
  padding: 5px !important;
  height: auto;

  .el-dialog__header {
    background-color: aliceblue;
    border-bottom: 1px solid #e6ebf5;
    padding-bottom: 15px !important;
  }

  .el-dialog__body {
    padding: 5px 0 0 5px !important;
  }
}

.el-dialog__body {
  padding: 5px 10px !important;
}

::v-deep.avatar-uploader .el-upload {
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
}

::v-deep.avatar-uploader .el-upload:hover {
  border-color: #409EFF;
}

::v-deep .avatar-uploader-icon {
  font-size: 28px;
  color: #8c939d;
  width: 60px;
  height: 60px;
  line-height: 64px;
  text-align: center;
}

::v-deep.avatar {
  width: 60px;
  height: 60px;
  display: block;
}

</style>
