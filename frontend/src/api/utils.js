/**
 * API工具函数
 * 用于字段过滤和类型转换，确保请求数据与后端DTO一致
 */

/**
 * 从对象中只提取指定字段
 * @param {Object} obj - 源对象
 * @param {string[]} fields - 允许的字段列表
 * @returns {Object} 只包含指定字段的新对象
 */
export function pick(obj, fields) {
  if (!obj || typeof obj !== "object") return {};
  const result = {};
  for (const field of fields) {
    if (field in obj && obj[field] !== undefined) {
      result[field] = obj[field];
    }
  }
  return result;
}

/**
 * 从对象中排除指定字段
 * @param {Object} obj - 源对象
 * @param {string[]} excludeFields - 要排除的字段列表
 * @returns {Object} 排除指定字段后的新对象
 */
export function omit(obj, excludeFields) {
  if (!obj || typeof obj !== "object") return {};
  const result = { ...obj };
  for (const field of excludeFields) {
    delete result[field];
  }
  return result;
}

/**
 * 数组转逗号分隔字符串
 * @param {number[]} arr
 * @returns {string|null}
 * @example arrayToString([1,2,3]) => "1,2,3"
 */
export function arrayToString(arr) {
  if (!arr || arr.length === 0) return null;
  return arr.join(",");
}

/**
 * 对象转JSON字符串
 * @param {Object} obj
 * @returns {string|null}
 */
export function toJson(obj) {
  if (!obj || Object.keys(obj).length === 0) return null;
  return JSON.stringify(obj);
}

/**
 * 判断值是否为空（null、undefined、空字符串、空数组、空对象）
 * @param {*} value
 * @returns {boolean}
 */
export function isEmpty(value) {
  if (value === null || value === undefined) return true;
  if (typeof value === 'string' && value.trim() === '') return true;
  if (Array.isArray(value) && value.length === 0) return true;
  if (typeof value === 'object' && Object.keys(value).length === 0) return true;
  return false;
}

/**
 * 移除对象中的空字段（null、undefined、空字符串）
 * 支持递归处理嵌套对象
 * @param {Object} obj - 源对象
 * @param {Object} options - 配置选项
 * @param {boolean} options.deep - 是否递归处理嵌套对象，默认 true
 * @param {boolean} options.removeEmptyString - 是否移除空字符串，默认 true
 * @param {boolean} options.removeEmptyArray - 是否移除空数组，默认 false
 * @param {boolean} options.removeEmptyObject - 是否移除空对象，默认 false
 * @returns {Object} 移除空字段后的新对象
 */
export function removeEmpty(obj, options = {}) {
  const {
    deep = true,
    removeEmptyString = true,
    removeEmptyArray = false,
    removeEmptyObject = false
  } = options;

  if (!obj || typeof obj !== 'object') return obj;
  
  // 处理数组
  if (Array.isArray(obj)) {
    return obj
      .map(item => deep && typeof item === 'object' ? removeEmpty(item, options) : item)
      .filter(item => !isEmpty(item) || !removeEmptyArray);
  }

  const result = {};
  for (const [key, value] of Object.entries(obj)) {
    // 跳过 null 和 undefined
    if (value === null || value === undefined) continue;
    
    // 跳过空字符串
    if (removeEmptyString && typeof value === 'string' && value.trim() === '') continue;
    
    // 递归处理嵌套对象
    if (deep && typeof value === 'object' && !Array.isArray(value)) {
      const cleaned = removeEmpty(value, options);
      // 跳过空对象
      if (removeEmptyObject && Object.keys(cleaned).length === 0) continue;
      result[key] = cleaned;
    } else if (Array.isArray(value)) {
      const cleaned = deep ? removeEmpty(value, options) : value;
      // 跳过空数组
      if (removeEmptyArray && cleaned.length === 0) continue;
      result[key] = cleaned;
    } else {
      result[key] = value;
    }
  }
  return result;
}

/**
 * 将对象转换为 JSON 字符串，自动过滤空字段
 * @param {Object} obj - 源对象
 * @param {Object} options - removeEmpty 的配置选项
 * @returns {string} JSON 字符串
 */
export function toJsonClean(obj, options = {}) {
  if (!obj || typeof obj !== 'object') return JSON.stringify(obj);
  const cleaned = removeEmpty(obj, options);
  return JSON.stringify(cleaned);
}
