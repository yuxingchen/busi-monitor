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
