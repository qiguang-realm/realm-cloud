<template>
  <div class="app-container">
    <el-form ref="dataForm" :rules="rules" :model="temp" label-position="left" label-width="35%"
             :disabled="dialogStatus==='detail'"
             style="width: 400px; margin-left:50px;">

      <el-form-item label="奖品名称:" prop="prizeName">
        <el-select ref="prizeOption" v-model="temp.prizeName" placeholder="请选择奖品" class="el-input"
                   @change="changePrize">
          <el-option
            v-for="item in prizeList"
            :key="item.id"
            :label="item.commodityName"
            :value="item.id"
            :data-url="item.commodityPicture"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="活动开始时间:" prop="startTime">
        <el-date-picker v-model="temp.startTime" placeholder="活动开始时间" type="datetime" value-format="yyyy-MM-dd HH:mm:ss"
                        class="el-input"/>
      </el-form-item>

      <el-form-item label="活动结束时间:" prop="endTime">
        <el-date-picker v-model="temp.endTime" placeholder="活动结束时间" type="datetime" value-format="yyyy-MM-dd HH:mm:ss"
                        class="el-input"/>
      </el-form-item>

      <!--      1.正数，不能输入小数和小数点，只能输入 正整数，大于0的      -->
      <el-form-item label="开奖券数:" prop="drawNum">
        <el-input v-model="temp.drawNum" type="number" min="0"
                  @input="temp.drawNum=temp.drawNum.replace(/^(0+)|[^\d]+/g,'')" placeholder="开奖券数"/>
      </el-form-item>

      <el-form-item label="积分:" prop="points">
        <el-input v-model="temp.points" type="number" min="0"
                  @input="temp.points=temp.points.replace(/^(0+)|[^\d]+/g,'')" placeholder="积分"/>
      </el-form-item>

    </el-form>

    <div v-if="dialogStatus!=='detail'" slot="footer" class="dialog-footer"
         style="text-align: center; margin-top: 20px;">
      <el-button type="primary" @click="onSubmit">确定</el-button>
    </div>

  </div>
</template>
<script>

import {
  createIntegralTreasureConfig,
  fetchIntegralTreasureConfig,
  updateIntegralTreasureConfig,
  queryCommodityList
} from '@/api/treasureConfig'

export default {
  name: 'CreateEdit',
  props: {
    id: {
      type: String,
      default: ''  // 字符串类型，默认''
    },
    dialogStatus: {
      type: String,
      default: ''  // 字符串类型，默认''
    },
  },
  data() {
    return {
      temp: {
        id: null,
        prizeId: '',
        prizeName: '',
        prizeType: '',
        winnerUserId: '',
        winnerUserName: '',
        periodId: '',
        drawNum: '',
        points: null,
        status: null,
        creator: null,
        createTime: null,
        updater: null,
        updateTime: null,
        prizeImage: null,
        startTime: null,
        endTime: null,
        drawStatus: null,
        drawTime: null,
        prizeStatus: null,
      },

      prizeList: [],

      // 表单校验
      rules: {
        prizeName: [
          {required: true, message: '请输入奖品名称', trigger: 'blur'}
        ],
        drawNum: [
          {required: true, message: '请输入开奖券数', trigger: 'blur'}
        ],
        points: [
          {required: true, message: '请输入积分', trigger: 'blur'}
        ],
        startTime: [
          {required: true, message: '请输入活动开始时间', trigger: 'blur'}
        ],
        endTime: [
          {required: true, message: '请输入活动结束时间', trigger: 'blur'}
        ],
      }
    }
  },
  created() {
    this.getGoodsList()
    if (this.id !== null) {
      this.getInfo(this.id)
    }
  },
  methods: {
    getInfo(id) {
      fetchIntegralTreasureConfig({id: id}).then(res => {
        if (res.code === '0') {
          this.temp = res.clazz
        }
      })
    },

    // 获取奖品列表
    getGoodsList() {
      queryCommodityList().then(res => {
        this.prizeList = res
      })
    },

    // 选择奖品
    changePrize(val) {
      const good = this.prizeList.find(r => r.id === val)
      if (good) {
        this.temp.prizeId = good.id
        this.temp.prizeName = good.commodityName
        this.temp.prizeType = good.commodityType
        this.temp.prizeImage = good.commodityPicture
      }
    },

    //提交按钮
    onSubmit: function () {
      this.$refs["dataForm"].validate(valid => {
        if (valid) {
          // this.temp.id = this.id
          (this.id === null ? createIntegralTreasureConfig(this.temp) : updateIntegralTreasureConfig(this.temp)).then(res => {
            if (res.code === '0') {
              this.$emit('fatherMethod')
            }
          })
        }
      });
    },

  }
}
</script>

<style scoped lang="scss">

</style>
