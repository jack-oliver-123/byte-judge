import { ref } from 'vue'

export type EditingLeaveState = 'saved' | 'unsaved' | 'busy'

export const editingLeaveState = ref<EditingLeaveState | null>(null)

export function setEditingState(state: EditingLeaveState | null) {
  editingLeaveState.value = state
}
