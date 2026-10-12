import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// 試験のあとに、描画した画面を片付ける (vitest の globals は使わない)
afterEach(() => {
  cleanup()
})
