#include <stdio.h>
#include <windows.h>

#define ENQUEUE_ERROR 0xffff
#define CODE_SIZE 1024

typedef HMODULE (WINAPI *GetModuleHandle_t)(LPCTSTR);
typedef FARPROC (WINAPI *GetProcAddress_t)(HMODULE, LPCSTR);
typedef int (__stdcall *JVM_EnqueueOperation_t)(char *, char *, char *, char *, char *);

typedef struct {
    GetModuleHandle_t GetModuleHandleA;
    GetProcAddress_t GetProcAddress;
    char library[32];
    char command[32];
    char pipe[MAX_PATH];
    char argument[4][MAX_PATH];
} EnqueueOperation;

#pragma check_stack(off)

/**
 * Executes the attachment on the remote thread. This method is executed on the target JVM and must not reference any addresses unknown to that address.
 * @param argument The argument provided by the JVM executing the attachment.
 * @return The result of the attachment.
 */
DWORD WINAPI execute_remote_attach
  (LPVOID argument) 
{
    printf("execute_remote_attach");
    fflush(stdout);
    EnqueueOperation *operation = (EnqueueOperation *) argument;
    HMODULE library = operation->GetModuleHandleA(operation->library);
    if (library != NULL) {
        JVM_EnqueueOperation_t JVM_EnqueueOperation = (JVM_EnqueueOperation_t) operation->GetProcAddress(library, operation->command);
        if (JVM_EnqueueOperation != NULL) {
            return (DWORD) JVM_EnqueueOperation(operation->argument[0],
                                                operation->argument[1],
                                                operation->argument[2],
                                                operation->argument[3],
                                                operation->pipe);
        }
    }
    return ENQUEUE_ERROR;
}

#pragma check_stack

/**
 * Allocates the code to execute on the remote machine.
 * @param process The process handle of the remote process to which to attach.
 * @return A pointer to the allocated code or {@code NULL} if the allocation failed.
 */
LPVOID allocate_code
  (HANDLE process) 
{
    printf("allocate_code");
    fflush(stdout);
    LPVOID code = VirtualAllocEx(process, NULL, CODE_SIZE, MEM_COMMIT, PAGE_EXECUTE_READWRITE);
    if (code == NULL) {
        return NULL;
    } else if (!WriteProcessMemory(process, code, execute_remote_attach, CODE_SIZE, NULL)) {
        VirtualFreeEx(process, NULL, CODE_SIZE, MEM_RELEASE);
        return NULL;
    } else {
        return code;
    }
}

/**
 * Allocates the argument to the remote execution.
 * @param process A handle to the remote process to which to attach.
 * @param pipe The name of the pipe to which the attachment result is written
 * @param argument0 The first argument to provide to the {@code JVM_EnqueueOperation}.
 * @param argument1 The second argument to provide to the {@code JVM_EnqueueOperation}.
 * @param argument2 The third argument to provide to the {@code JVM_EnqueueOperation}.
 * @param argument3 The forth argument to provide to the {@code JVM_EnqueueOperation}.
 * @return A pointer to the allocated argument or {@code NULL} if the allocation was not possible.
 */
LPVOID allocate_remote_argument
  (HANDLE process, const char *pipe, const char *argument0, const char *argument1, const char *argument2, const char *argument3) 
{
    printf("allocate_remote_argument");
    fflush(stdout);
    EnqueueOperation operation;
    operation.GetModuleHandleA = GetModuleHandleA;
    operation.GetProcAddress = GetProcAddress;
    strcpy(operation.library, "jvm");
    strcpy(operation.command, "JVM_EnqueueOperation");
    strcpy(operation.pipe, pipe);
    strcpy(operation.argument[0], argument0 == NULL ? "" : argument0);
    strcpy(operation.argument[1], argument1 == NULL ? "" : argument1);
    strcpy(operation.argument[2], argument2 == NULL ? "" : argument2);
    strcpy(operation.argument[3], argument3 == NULL ? "" : argument3);
    LPVOID allocation = VirtualAllocEx(process, NULL, sizeof(EnqueueOperation), MEM_COMMIT, PAGE_READWRITE);
    if (allocation == NULL) {
        return NULL;
    } else if (!WriteProcessMemory(process, allocation, &process, sizeof(process), NULL)) {
        VirtualFreeEx(process, NULL, sizeof(EnqueueOperation), MEM_RELEASE);
        return NULL;
    } else {
        return allocation;
    }
}
