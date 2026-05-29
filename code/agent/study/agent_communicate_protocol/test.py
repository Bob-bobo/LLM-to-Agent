from typing import List


class test:
    """
    Description: 
    Author: Administrator
    Date: 2026/5/26
    """
    def candy(self, ratings: List[int]) -> int:
        size = len(ratings)
        left = [1 for _ in range(size)]
        right = left[:]
        for i in range(1, size):
            if ratings[i] > ratings[i-1]: left[i] = left[i-1] + 1
        count = left[-1]
        for i in range(size - 2, -1, -1):
            if ratings[i] > ratings[i+1]: right[i] = right[i+1] + 1
            count += max(left[i], right[i])
        return count

    def trap(self, height: List[int]) -> int:
        res, left_index, right_index = 0, 0, len(height)-1
        left_max, right_max = height[left_index], height[right_index]
        left_index += 1
        right_index -= 1
        while left_index <= right_index:
            left_max = max(left_max, height[left_index])
            right_max = max(right_max, height[right_index])
            # 如果左侧最大值小于右侧最大值，则按照左侧计算
            if left_max < right_max:
                res += left_max - height[left_index]
                left_index += 1
            else:
                res += right_max - height[right_index]
                right_index -= 1
        return res


if __name__ == "__main__":
    a = [0,1,0,2,1,0,1,3,2,1,2,1] # 6
    b = [4,2,0,3,2,5] # 9
    print(test().trap(a))
    print(test().trap(b))
