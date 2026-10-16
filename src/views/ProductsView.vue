<script setup lang="ts">
import { computed, ref } from 'vue'
import { Box, Check, ChevronRight, CirclePlus, Copy, Edit3, Layers3, MoreHorizontal, PackageOpen, Plus, Radio, Ruler, Trash2, X } from 'lucide-vue-next'
import type { Product, PropertyDefinition, PropertyType, PlatformState } from '../domain/types'

const props = defineProps<{ state: PlatformState }>()
const emit = defineEmits<{ addProduct: [product: Omit<Product, 'id' | 'createdAt' | 'protocol'>]; updateProduct: [product: Product]; deleteProduct: [productId: string]; addDevice: [input: Pick<{ name: string; productId: string; code: string; location: string; description: string }, 'name' | 'productId' | 'code' | 'location' | 'description'>] }>()
const selectedProductId = ref(props.state.products[0]?.id ?? '')
// 模板作用域不包含浏览器全局对象，显式绑定以便在模板中复制主题。
const navigator = window.navigator
const showProductForm = ref(false)
const showDeviceForm = ref(false)
const editingProductId = ref<string | null>(null)
const productDraft = ref<Product>(newProduct())
const deviceDraft = ref({ name: '', productId: props.state.products[0]?.id ?? '', code: '', location: '', description: '' })
const productError = ref('')
const deviceError = ref('')
const selectedProduct = computed(() => props.state.products.find((product) => product.id === selectedProductId.value) ?? props.state.products[0])
const selectedDeviceCount = computed(() => props.state.devices.filter((device) => device.productId === selectedProduct.value?.id).length)

function newProduct(): Product { return { id: '', name: '', category: '环境监测', description: '', status: 'draft', protocol: 'MQTT', properties: [newProperty('number')], createdAt: new Date().toISOString() } }
function newProperty(type: PropertyType = 'number'): PropertyDefinition { return { id: crypto.randomUUID(), name: '', key: '', type, unit: '' } }
function selectProduct(id: string) { selectedProductId.value = id }
function openNewProduct() { editingProductId.value = null; productDraft.value = newProduct(); productError.value = ''; showProductForm.value = true }
function openEditProduct(product: Product) { editingProductId.value = product.id; productDraft.value = structuredClone(product); productError.value = ''; showProductForm.value = true }
function addProperty() { productDraft.value.properties.push(newProperty()) }
function removeProperty(index: number) { if (productDraft.value.properties.length > 1) productDraft.value.properties.splice(index, 1) }
function validateProduct() {
  if (!productDraft.value.name.trim()) return '请输入产品名称'
  if (productDraft.value.properties.some((property) => !property.name.trim() || !property.key.trim())) return '请完整填写属性名称和标识符'
  return ''
}
function saveProduct() {
  productError.value = validateProduct()
  if (productError.value) return
  if (editingProductId.value) emit('updateProduct', { ...productDraft.value })
  else emit('addProduct', { name: productDraft.value.name, category: productDraft.value.category, description: productDraft.value.description, status: productDraft.value.status, properties: productDraft.value.properties })
  showProductForm.value = false
}
function removeProduct(product: Product) {
  if (window.confirm(`确定删除产品“${product.name}”吗？`)) {
    emit('deleteProduct', product.id)
    if (selectedProductId.value === product.id) selectedProductId.value = props.state.products.find((item) => item.id !== product.id)?.id ?? ''
  }
}
function openNewDevice() { deviceDraft.value = { name: '', productId: selectedProduct.value?.id ?? props.state.products[0]?.id ?? '', code: '', location: '', description: '' }; deviceError.value = ''; showDeviceForm.value = true }
function saveDevice() {
  if (!deviceDraft.value.name.trim() || !deviceDraft.value.code.trim() || !deviceDraft.value.location.trim()) { deviceError.value = '请填写设备名称、编号和位置'; return }
  emit('addDevice', { ...deviceDraft.value, description: deviceDraft.value.description || '通过 OpenIOT 平台接入的家庭设备。' })
  showDeviceForm.value = false
}
function propertyTypeLabel(type: PropertyType) { return type === 'number' ? '数字' : type === 'boolean' ? '布尔值' : type === 'enum' ? '枚举' : '字符串' }
</script>

<template>
  <div class="page-stack">
    <section class="page-heading"><div><div class="breadcrumb">平台 <ChevronRight :size="13" /> 设备模型</div><h1>产品与属性</h1><p>定义一类设备的共同能力，设备接入时会继承这些属性。</p></div><button class="primary-button" @click="openNewProduct"><Plus :size="17"/> 新建产品</button></section>
    <section class="product-layout">
      <aside class="surface product-sidebar"><div class="product-sidebar-heading"><div><strong>产品目录</strong><small>{{ state.products.length }} 个产品</small></div><button class="icon-button" aria-label="新建产品" @click="openNewProduct"><Plus :size="16"/></button></div><div class="product-list"><button v-for="product in state.products" :key="product.id" class="product-list-item" :class="{ selected: selectedProduct?.id === product.id }" @click="selectProduct(product.id)"><span class="product-type-symbol" :class="product.category === '环境监测' ? 'blue' : product.category === '红外网关' ? 'orange' : 'green'"><Ruler v-if="product.category === '环境监测'" :size="17"/><Radio v-else-if="product.category === '红外网关'" :size="17"/><Box v-else :size="17"/></span><span><strong>{{ product.name }}</strong><small>{{ product.category }} · {{ state.devices.filter((device) => device.productId === product.id).length }} 台设备</small></span><ChevronRight :size="14" class="row-chevron"/></button></div><div class="sidebar-note"><Layers3 :size="17"/><span>产品属性是设备数据模型的基础，支持数字、字符串、布尔值和枚举。</span></div></aside>
      <section v-if="selectedProduct" class="surface product-detail"><header class="product-detail-header"><div class="product-detail-title"><span class="detail-product-icon" :class="selectedProduct.category === '环境监测' ? 'blue' : selectedProduct.category === '红外网关' ? 'orange' : 'green'"><Ruler v-if="selectedProduct.category === '环境监测'" :size="21"/><Radio v-else-if="selectedProduct.category === '红外网关'" :size="21"/><Box v-else :size="21"/></span><div><div class="section-kicker">{{ selectedProduct.category }} · {{ selectedProduct.protocol }}</div><h2>{{ selectedProduct.name }}</h2><p>{{ selectedProduct.description || '暂无产品描述' }}</p></div></div><div class="product-actions"><button class="icon-button" aria-label="编辑产品" data-tooltip="编辑产品" @click="openEditProduct(selectedProduct)"><Edit3 :size="16"/></button><button class="icon-button danger-icon" aria-label="删除产品" data-tooltip="删除产品" @click="removeProduct(selectedProduct)"><Trash2 :size="16"/></button></div></header><div class="product-summary-strip"><div><span>属性数量</span><strong>{{ selectedProduct.properties.length }}</strong></div><div><span>绑定设备</span><strong>{{ selectedDeviceCount }}</strong></div><div><span>产品状态</span><strong class="product-status"><i></i>{{ selectedProduct.status === 'active' ? '已发布' : '草稿' }}</strong></div><button class="secondary-button" @click="openNewDevice"><Plus :size="15"/> 创建设备</button></div><section class="properties-section"><div class="subsection-heading"><div><h3>设备属性</h3><p>设备通过 MQTT 上报时使用属性标识符作为数据键。</p></div><button class="text-action" @click="openEditProduct(selectedProduct)"><Edit3 :size="14"/> 编辑属性</button></div><div class="property-table"><div class="property-row property-heading"><span>属性名称</span><span>标识符</span><span>数据类型</span><span>单位</span><span>取值范围</span></div><div v-for="property in selectedProduct.properties" :key="property.id" class="property-row"><span><strong>{{ property.name }}</strong><small>{{ property.key }}</small></span><code>{{ property.key }}</code><span class="property-type"><i :class="property.type"></i>{{ propertyTypeLabel(property.type) }}</span><span>{{ property.unit || '—' }}</span><span>{{ property.min !== undefined || property.max !== undefined ? `${property.min ?? '—'} ~ ${property.max ?? '—'}` : property.options?.join(' / ') || '—' }}</span></div></div></section><section class="mqtt-contract"><div class="contract-heading"><Radio :size="17"/><div><strong>MQTT 数据约定</strong><span>接入设备使用以下主题上报属性数据</span></div></div><code>openiot/设备编号/telemetry</code><button class="icon-button" aria-label="复制 MQTT 主题" data-tooltip="复制主题" @click="navigator.clipboard?.writeText(`openiot/{deviceCode}/telemetry`)"><Copy :size="15"/></button></section></section>
    </section>

    <Teleport to="body"><div v-if="showProductForm" class="modal-backdrop" @click.self="showProductForm = false"><section class="modal-card form-modal wide-modal" role="dialog" aria-modal="true" aria-label="产品配置"><header class="modal-header"><div><div class="modal-kicker">设备模型</div><h2>{{ editingProductId ? '编辑产品' : '新建产品' }}</h2></div><button class="icon-button" aria-label="关闭" @click="showProductForm = false"><X :size="18"/></button></header><div class="form-grid"><label class="form-field"><span>产品名称</span><input v-model="productDraft.name" class="form-control" placeholder="例如：智能空气质量传感器"/></label><label class="form-field"><span>产品分类</span><select v-model="productDraft.category" class="form-control"><option>环境监测</option><option>开关控制</option><option>红外网关</option><option>其他设备</option></select></label><label class="form-field full"><span>产品描述</span><textarea v-model="productDraft.description" class="form-control" rows="2" placeholder="描述设备的用途和接入能力"></textarea></label></div><div class="property-editor"><div class="property-editor-heading"><div><h3>属性定义</h3><p>每个属性需要唯一标识符，设备上报时作为数据键使用。</p></div><button class="secondary-button small-button" @click="addProperty"><Plus :size="15"/> 添加属性</button></div><div v-for="(property, index) in productDraft.properties" :key="property.id" class="property-editor-row"><input v-model="property.name" class="form-control" placeholder="属性名称"/><input v-model="property.key" class="form-control" placeholder="标识符，如 temperature"/><select v-model="property.type" class="form-control"><option value="number">数字</option><option value="string">字符串</option><option value="boolean">布尔值</option><option value="enum">枚举</option></select><input v-model="property.unit" class="form-control" placeholder="单位"/><input v-if="property.type === 'number'" v-model.number="property.min" type="number" class="form-control" placeholder="最小值"/><input v-if="property.type === 'number'" v-model.number="property.max" type="number" class="form-control" placeholder="最大值"/><button class="icon-button danger-icon" aria-label="删除属性" @click="removeProperty(index)"><Trash2 :size="15"/></button></div></div><p v-if="productError" class="form-error">{{ productError }}</p><footer class="modal-footer"><button class="secondary-button" @click="showProductForm = false">取消</button><button class="primary-button" @click="saveProduct"><Check :size="16"/> 保存产品</button></footer></section></div><div v-if="showDeviceForm" class="modal-backdrop" @click.self="showDeviceForm = false"><section class="modal-card form-modal" role="dialog" aria-modal="true" aria-label="创建设备"><header class="modal-header"><div><div class="modal-kicker">设备接入</div><h2>创建设备</h2></div><button class="icon-button" aria-label="关闭" @click="showDeviceForm = false"><X :size="18"/></button></header><div class="form-grid"><label class="form-field full"><span>绑定产品</span><select v-model="deviceDraft.productId" class="form-control"><option v-for="product in state.products" :key="product.id" :value="product.id">{{ product.name }}</option></select></label><label class="form-field"><span>设备名称</span><input v-model="deviceDraft.name" class="form-control" placeholder="例如：书房灯"/></label><label class="form-field"><span>设备编号</span><input v-model="deviceDraft.code" class="form-control" placeholder="例如：OI-240042"/></label><label class="form-field full"><span>安装位置</span><input v-model="deviceDraft.location" class="form-control" placeholder="例如：二层 · 书房"/></label><label class="form-field full"><span>备注</span><textarea v-model="deviceDraft.description" class="form-control" rows="3" placeholder="设备用途或接入说明"></textarea></label></div><p v-if="deviceError" class="form-error">{{ deviceError }}</p><footer class="modal-footer"><button class="secondary-button" @click="showDeviceForm = false">取消</button><button class="primary-button" @click="saveDevice"><Check :size="16"/> 创建设备</button></footer></section></div></Teleport>
  </div>
</template>
